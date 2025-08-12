package net.portswigger;

import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.StringRequestContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "client", mixinStandardHelpOptions = true, version = "1.0",
         description = "HTTP client with AWS authentication support")
public class Client implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(Client.class);
    
    @Parameters(index = "0", description = "The URL to request")
    private String url;
    
    @Option(names = {"-m", "--method"}, description = "HTTP method (default: GET)", defaultValue = "GET")
    private String method;
    
    @Option(names = {"-b", "--body"}, description = "Request body for POST/PUT requests")
    private String body;
    
    public static void main(String[] args) {
        int exitCode = new CommandLine(new Client()).execute(args);
        System.exit(exitCode);
    }
    
    @Override
    public Integer call() throws Exception {
        URI uri = URI.create(url);
        
        HttpClient client = new HttpClient();
        
        try {
            client.start();
            
            Request request = client.newRequest(uri).method(method);
            
            if (body != null && !body.isEmpty()) {
                request.body(new StringRequestContent(body));
            }
            
            if (url.contains("sts.amazonaws.com") || url.contains("sts.")) {
                addAwsAuthHeaders(request, uri, method, body);
            }
            
            logger.info("Request headers:");
            request.getHeaders().forEach(field -> 
                logger.info("  {}: {}", field.getName(), field.getValue()));
            
            ContentResponse response = request.send();
            
            System.out.println("Status: " + response.getStatus());
            System.out.println("Headers:");
            response.getHeaders().forEach((field) -> 
                System.out.println(field.getName() + ": " + field.getValue()));
            System.out.println();
            System.out.println(response.getContentAsString());
            
            return 0;
            
        } catch (Exception e) {
            logger.error("Request failed", e);
            return 1;
        } finally {
            client.stop();
        }
    }
    
    private static void addAwsAuthHeaders(Request request, URI uri, String method, String body) {
        try {
            String region = extractRegionFromUri(uri);
            
            SdkHttpMethod httpMethod = SdkHttpMethod.fromValue(method.toUpperCase());
            SdkHttpFullRequest.Builder requestBuilder = SdkHttpFullRequest.builder()
                    .method(httpMethod)
                    .uri(uri)
                    .putHeader("Host", uri.getHost());
            
            if (body != null && !body.isEmpty()) {
                requestBuilder.contentStreamProvider(() -> new java.io.ByteArrayInputStream(body.getBytes()));
                requestBuilder.putHeader("Content-Type", "application/x-www-form-urlencoded");
            }
            
            SdkHttpFullRequest sdkRequest = requestBuilder.build();
            
            Aws4SignerParams signerParams = Aws4SignerParams.builder()
                    .awsCredentials(DefaultCredentialsProvider.create().resolveCredentials())
                    .signingName("sts")
                    .signingRegion(Region.of(region))
                    .build();
            
            Aws4Signer signer = Aws4Signer.create();
            SdkHttpFullRequest signedRequest = signer.sign(sdkRequest, signerParams);
            
            for (Map.Entry<String, java.util.List<String>> header : signedRequest.headers().entrySet()) {
                for (String value : header.getValue()) {
                    request.headers(headers -> headers.add(header.getKey(), value));
                }
            }
            
            logger.info("Added AWS authentication headers for region: {} and method: {}", region, method);
            
        } catch (Exception e) {
            logger.error("Failed to add AWS authentication headers", e);
            throw new RuntimeException("Failed to add AWS authentication headers", e);
        }
    }
    
    private static String extractRegionFromUri(URI uri) {
        String host = uri.getHost();
        if (host.contains("sts.amazonaws.com")) {
            return "us-east-1";
        }
        if (host.matches("sts\\.[a-z0-9-]+\\.amazonaws\\.com")) {
            String[] parts = host.split("\\.");
            if (parts.length >= 3) {
                return parts[1];
            }
        }
        return "us-east-1";
    }
}