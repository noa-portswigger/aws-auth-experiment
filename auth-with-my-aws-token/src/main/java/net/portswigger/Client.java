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
                    String subjectToken = TokenGenerator.generateSubjectToken(audience, region);
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
}