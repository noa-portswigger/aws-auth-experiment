package net.portswigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
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
    
    @Option(names = {"--add-auth"}, description = "Add AWS federated identity authorization header")
    private boolean addAuth;
    
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
            
            // Add AWS federated identity authorization header if requested
            if (addAuth) {
                try {
                    String audience = "//iam.googleapis.com/projects/252090236040/locations/global/workloadIdentityPools/nnoare/providers/nnoare";
                    Region region = Region.US_EAST_1;
                    String subjectToken = generateSubjectToken(audience, region);
                    String authHeader = "aws-fed-id " + subjectToken;
                    request.headers(headers -> headers.add("Authorization", authHeader));
                    logger.info("Added AWS federated identity authorization header");
                } catch (Exception e) {
                    logger.error("Failed to generate subject token", e);
                    return 1;
                }
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

    /**
     * Generate an AWS subject token.
     *
     * @param audience The GCP workload identity pool audience
     * @return URL-encoded JSON of AWS STS GetCallerIdentity request, the way GCP expects it
     */
    public String generateSubjectToken(String audience, Region region) {
        try {
            if (audience == null || audience.trim().isEmpty()) {
                throw new RuntimeException("Audience is required for AWS subject token generation");
            }

            // Create AWS STS GetCallerIdentity request URL
            String baseUrl = String.format("https://sts.%s.amazonaws.com/", region.id());
            String queryString = "Action=GetCallerIdentity&Version=2011-06-15";
            String fullUrl = baseUrl + "?" + queryString;

            // Prepare the x-goog-cloud-target-resource header
            String targetResource = audience.replace("https://", "");

            // Create the request with all required headers
            Map<String, String> headers = new HashMap<>();
            headers.put("Host", String.format("sts.%s.amazonaws.com", region.id()));
            headers.put("x-goog-cloud-target-resource", targetResource);

            // Build the HTTP request
            SdkHttpFullRequest.Builder requestBuilder = SdkHttpFullRequest.builder()
                    .method(SdkHttpMethod.POST)
                    .uri(URI.create(fullUrl));

            // Add headers
            for (Map.Entry<String, String> header : headers.entrySet()) {
                requestBuilder.putHeader(header.getKey(), header.getValue());
            }

            SdkHttpFullRequest request = requestBuilder.build();

            // Sign the request using AWS Signature Version 4
            Aws4Signer signer = Aws4Signer.create();
            Aws4SignerParams signerParams = Aws4SignerParams.builder()
                    .awsCredentials(DefaultCredentialsProvider.create().resolveCredentials())
                    .signingName("sts")
                    .signingRegion(region)
                    .build();

            SdkHttpFullRequest signedRequest = signer.sign(request, signerParams);

            ObjectMapper objectMapper = new ObjectMapper();
            // Create headers array in the required format for GCP
            ArrayNode headersArray = objectMapper.createArrayNode();
            for (Map.Entry<String, List<String>> header : signedRequest.headers().entrySet()) {
                // AWS SDK may have multiple values for a header, we take the first one
                String value = header.getValue().isEmpty() ? "" : header.getValue().get(0);
                ObjectNode headerObj = objectMapper.createObjectNode();
                headerObj.put("key", header.getKey().toLowerCase());
                headerObj.put("value", value);
                headersArray.add(headerObj);
            }

            // Create the token payload in the exact format GCP expects
            ObjectNode tokenData = objectMapper.createObjectNode();
            tokenData.put("url", fullUrl);
            tokenData.put("method", request.method().name());
            tokenData.set("headers", headersArray);

            // Convert to JSON and URL-encode
            String tokenJson = objectMapper.writeValueAsString(tokenData);
            return URLEncoder.encode(tokenJson, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate subject token: " + e.getMessage(), e);
        }
    }

}