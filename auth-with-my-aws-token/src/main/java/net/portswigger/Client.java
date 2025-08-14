package net.portswigger;

import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import software.amazon.awssdk.regions.Region;

import java.net.URI;
import java.util.concurrent.Callable;

@Command(name = "client", mixinStandardHelpOptions = true, version = "1.0",
         description = "HTTP GET client with AWS authentication support")
public class Client implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(Client.class);
    
    // This references noa's personal gcp project with configured identity pool and provider. 
    // Useful for verifying that GCP can use our token
    private static final String GCP_AUDIENCE = "//iam.googleapis.com/projects/252090236040/locations/global/workloadIdentityPools/nnoare/providers/nnoare";
    private static final Region STS_REGION = Region.EU_WEST_1;
    
    @Parameters(index = "0", description = "The URL to request")
    private String url;
    
    @Option(names = {"--add-auth"}, description = "Add AWS federated identity authorization header")
    private boolean addAuth;
    
    @Option(names = {"--gcp-compatible-audience"}, description = "Use GCP-compatible hardcoded audience instead of URL-based audience")
    private boolean gcpCompatibleAudience = false;
    
    public static void main(String[] args) {
        int exitCode = new CommandLine(new Client()).execute(args);
        System.exit(exitCode);
    }
    
    @Override
    public Integer call() throws Exception {
        URI uri = URI.create(url);
        
        try (HttpClient client = new HttpClient()) {
            client.start();

            Request request = client.newRequest(uri);

            // Add AWS federated identity authorization header if requested
            if (addAuth) {
                try {
                    String authHeader = makeAuthorization(gcpCompatibleAudience ? GCP_AUDIENCE : makeAudience(url));
                    request.headers(hs -> hs.add("Authorization", authHeader));
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
        }
    }
    
    private String makeAudience(String url) {
        URI uri = URI.create(url);
        String host = uri.getHost();
        int port = uri.getPort();
        if (port == -1) {
            // Use default port based on scheme
            port = "https".equals(uri.getScheme()) ? 443 : 80;
        }
        return host + ":" + port;
    }
    
    private String makeAuthorization(String audience) throws Exception {
        String subjectToken = TokenGenerator.generateSubjectToken(audience, STS_REGION);
        return "aws-fed-id " + subjectToken;
    }
}