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

public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);
    private static final String GCP_STS_URL = "https://sts.googleapis.com/v1/token";
    private static final String AUDIENCE = "//iam.googleapis.com/projects/252090236040/locations/global/workloadIdentityPools/nnoare/providers/nnoare";

    public static void main(String[] args) throws Exception {
        HttpClient httpClient = new HttpClient();
        httpClient.start();
        
        org.eclipse.jetty.server.Server server = new org.eclipse.jetty.server.Server(8080);
        
        server.setHandler(new org.eclipse.jetty.server.Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) throws Exception {
                String authHeader = request.getHeaders().get("Authorization");
                
                if (authHeader != null && authHeader.startsWith("aws-fed-id ")) {
                    String token = authHeader.substring("aws-fed-id ".length()).trim();
                    logger.info("Found aws-fed-id authorization header with token");
                    
                    try {
                        String gcpResponse = exchangeTokenForGcpCredentials(httpClient, token);
                        
                        response.setStatus(200);
                        response.getHeaders().put("Content-Type", "application/json; charset=utf-8");
                        response.write(true, java.nio.ByteBuffer.wrap(gcpResponse.getBytes()), callback);
                        
                        logger.info("Successfully exchanged token for GCP credentials");
                        return true;
                    } catch (Exception e) {
                        logger.error("Failed to exchange token for GCP credentials", e);
                        
                        response.setStatus(500);
                        response.getHeaders().put("Content-Type", "application/json; charset=utf-8");
                        String errorResponse = "{\"error\":\"Failed to exchange token: " + e.getMessage() + "\"}";
                        response.write(true, java.nio.ByteBuffer.wrap(errorResponse.getBytes()), callback);
                        return true;
                    }
                } else {
                    response.setStatus(200);
                    response.getHeaders().put("Content-Type", "text/html; charset=utf-8");
                    
                    String html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <title>AWS Token to GCP Auth Server</title>
                        </head>
                        <body>
                            <h1>AWS Token to GCP Auth Server</h1>
                            <p>Send a request with Authorization header: aws-fed-id &lt;token&gt;</p>
                            <p>Request URI: %s</p>
                            <p>Method: %s</p>
                            <p>Authorization Header: %s</p>
                        </body>
                        </html>
                        """.formatted(request.getHttpURI().getPath(), request.getMethod(), authHeader != null ? authHeader : "None");
                    
                    response.write(true, java.nio.ByteBuffer.wrap(html.getBytes()), callback);
                    return true;
                }
            }
        });
        
        server.start();
        logger.info("Server started on http://localhost:8080");
        server.join();
    }
    
    private static String exchangeTokenForGcpCredentials(HttpClient httpClient, String subjectToken) throws Exception {
        String formData = "audience=" + URLEncoder.encode(AUDIENCE, StandardCharsets.UTF_8) +
                "&grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:token-exchange", StandardCharsets.UTF_8) +
                "&requested_token_type=" + URLEncoder.encode("urn:ietf:params:oauth:token-type:access_token", StandardCharsets.UTF_8) +
                "&scope=" + URLEncoder.encode("https://www.googleapis.com/auth/cloud-platform", StandardCharsets.UTF_8) +
                "&subject_token_type=" + URLEncoder.encode("urn:ietf:params:aws:token-type:aws4_request", StandardCharsets.UTF_8) +
                "&subject_token=" + URLEncoder.encode(subjectToken, StandardCharsets.UTF_8);
        
        ContentResponse response = httpClient.newRequest(GCP_STS_URL)
                .method(HttpMethod.POST)
                .headers(headers -> headers.put("Content-Type", "application/x-www-form-urlencoded"))
                .body(new StringRequestContent(formData))
                .send();
        
        if (response.getStatus() != 200) {
            throw new RuntimeException("GCP STS API returned status " + response.getStatus() + ": " + response.getContentAsString());
        }
        
        return response.getContentAsString();
    }
}