package net.portswigger;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.StringRequestContent;
import org.eclipse.jetty.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class TokenExchangeHandler extends org.eclipse.jetty.server.Handler.Abstract implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(TokenExchangeHandler.class);
    private static final String GCP_STS_URL = "https://sts.googleapis.com/v1/token";
    private static final String AUDIENCE = "//iam.googleapis.com/projects/252090236040/locations/global/workloadIdentityPools/nnoare/providers/nnoare";
    
    private final HttpClient httpClient;
    
    public TokenExchangeHandler() throws Exception {
        this.httpClient = new HttpClient();
        this.httpClient.start();
    }
    
    @Override
    public void close() throws Exception {
        if (httpClient != null) {
            httpClient.stop();
        }
    }
    
    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        try {
            String authHeader = request.getHeaders().get("Authorization");
            var validAuthToken = false;
            if (authHeader != null && authHeader.startsWith("aws-fed-id ")) {
                exchangeTokenForGcpCredentials(authHeader.substring("aws-fed-id ".length()).trim());
                validAuthToken = true;
            }

            response.setStatus(200);
            response.getHeaders().put("Content-Type", "text/html; charset=utf-8");
            response.write(true, java.nio.ByteBuffer.wrap(generateHtmlPage(validAuthToken).getBytes()), callback);
            return true;
        } catch (Exception e) {
            callback.failed(e);
            return true;
        }
    }

    private String generateHtmlPage(boolean validAuthToken) {
        String message = validAuthToken 
            ? "The request contained a valid AWS authentication token according to google"
            : "There was no authentication token";
            
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>AWS Token to GCP Auth Server</title>
            </head>
            <body>
                <h1>AWS Token to GCP Auth Server</h1>
                <p>%s</p>
            </body>
            </html>
            """.formatted(message);
    }
    
    private void exchangeTokenForGcpCredentials(String subjectToken) throws Exception {
        String formData = "audience=" + URLEncoder.encode(AUDIENCE, StandardCharsets.UTF_8) +
                "&grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:token-exchange", StandardCharsets.UTF_8) +
                "&requested_token_type=" + URLEncoder.encode("urn:ietf:params:oauth:token-type:access_token", StandardCharsets.UTF_8) +
                "&scope=" + URLEncoder.encode("https://www.googleapis.com/auth/cloud-platform", StandardCharsets.UTF_8) +
                "&subject_token_type=" + URLEncoder.encode("urn:ietf:params:aws:token-type:aws4_request", StandardCharsets.UTF_8) +
                "&subject_token=" + URLEncoder.encode(subjectToken, StandardCharsets.UTF_8);
        
        ContentResponse response = this.httpClient.newRequest(GCP_STS_URL)
                .method(HttpMethod.POST)
                .headers(headers -> headers.put("Content-Type", "application/x-www-form-urlencoded"))
                .body(new StringRequestContent(formData))
                .send();
        
        if (response.getStatus() != 200) {
            throw new RuntimeException("GCP STS API returned status " + response.getStatus() + ": " + response.getContentAsString());
        }
    }
}