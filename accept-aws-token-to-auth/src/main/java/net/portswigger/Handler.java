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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class Handler extends org.eclipse.jetty.server.Handler.Abstract implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(Handler.class);
    private static final String GCP_STS_URL = "https://sts.googleapis.com/v1/token";
    private static final String AUDIENCE = "//iam.googleapis.com/projects/252090236040/locations/global/workloadIdentityPools/nnoare/providers/nnoare";
    
    private final HttpClient httpClient;
    private final boolean checkTokenAgainstGcp;
    
    public Handler(boolean checkTokenAgainstGcp) throws Exception {
        this.httpClient = new HttpClient();
        this.httpClient.start();
        this.checkTokenAgainstGcp = checkTokenAgainstGcp;
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
            String validationResult = null;
            
            if (authHeader != null && authHeader.startsWith("aws-fed-id ")) {
                String token = authHeader.substring("aws-fed-id ".length()).trim();
                
                if (checkTokenAgainstGcp) {
                    exchangeTokenForGcpCredentials(token);
                    validationResult = "gcp";
                } else {
                    validationResult = exchangeTokenForAwsRole(token);
                }
            }

            response.setStatus(200);
            response.getHeaders().put("Content-Type", "text/html; charset=utf-8");
            response.write(true, java.nio.ByteBuffer.wrap(generateHtmlPage(validationResult).getBytes()), callback);
            return true;
        } catch (Exception e) {
            callback.failed(e);
            return true;
        }
    }

    private String generateHtmlPage(String validationResult) {
        String message;
        if (validationResult == null) {
            message = "There was no authentication token";
        } else if (validationResult.equals("gcp")) {
            message = "The request contained a valid AWS authentication token according to google";
        } else {
            message = "The request contained a valid AWS authentication token for role ARN: " + validationResult;
        }
            
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
    
    private String exchangeTokenForAwsRole(String subjectToken) throws Exception {
        // URL decode the token
        String decodedToken = URLDecoder.decode(subjectToken, StandardCharsets.UTF_8);
        
        // Parse the JSON token
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode tokenData = objectMapper.readTree(decodedToken);

        // Create the request to AWS STS
        org.eclipse.jetty.client.Request request = this.httpClient.newRequest(tokenData.get("url").asText())
                .method(HttpMethod.valueOf(tokenData.get("method").asText()));
        
        // Add all headers from the token
        for (JsonNode header : tokenData.get("headers")) {
            String key = header.get("key").asText();
            String value = header.get("value").asText();
            request.headers(h -> h.add(key, value));
        }
        
        ContentResponse response = request.send();
        
        if (response.getStatus() != 200) {
            throw new RuntimeException("AWS STS API returned status " + response.getStatus() + ": " + response.getContentAsString());
        }
        
        // Parse the XML response to extract role information
        String responseContent = response.getContentAsString();
        return parseAwsRoleFromResponse(responseContent);
    }
    
    private String parseAwsRoleFromResponse(String xmlResponse) {
        try {
            XmlMapper xmlMapper = new XmlMapper();
            JsonNode rootNode = xmlMapper.readTree(xmlResponse);
            
            // Look for ARN specifically under GetCallerIdentityResult
            JsonNode getCallerIdentityResult = rootNode.path("GetCallerIdentityResult");
            if (getCallerIdentityResult.isMissingNode()) {
                throw new RuntimeException("GetCallerIdentityResult not found in AWS STS response");
            }
            
            JsonNode arnNode = getCallerIdentityResult.path("Arn");
            if (arnNode.isMissingNode()) {
                throw new RuntimeException("Arn not found under GetCallerIdentityResult in AWS STS response");
            }
            
            return arnNode.asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AWS STS response: " + e.getMessage(), e);
        }
    }
}