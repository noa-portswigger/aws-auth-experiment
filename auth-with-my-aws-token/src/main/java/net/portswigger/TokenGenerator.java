package net.portswigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4FamilyHttpSigner;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.SignedRequest;
import software.amazon.awssdk.identity.spi.AwsCredentialsIdentity;
import software.amazon.awssdk.regions.Region;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TokenGenerator {
    /**
     * Generate an AWS subject token.
     *
     * @param audience The GCP workload identity pool audience
     * @return URL-encoded JSON of AWS STS GetCallerIdentity request, the way GCP expects it
     */
    public static String generateSubjectToken(String audience, Region region) {
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

            AwsCredentials credentials = DefaultCredentialsProvider.create().resolveCredentials();


            // For now, google requires the deprecated version.
            // See https://github.com/googleapis/google-auth-library-java/issues/1792 for details.
            Aws4Signer signer = Aws4Signer.create();
            Aws4SignerParams signerParams = Aws4SignerParams.builder()
                    .awsCredentials(credentials)
                    .signingName("sts")
                    .signingRegion(region)
                    .build();

            SdkHttpFullRequest signedRequest = signer.sign(request, signerParams);

            return createTokenFromSignedRequest(signedRequest);

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate subject token: " + e.getMessage(), e);
        }
    }

    private static String createTokenFromSignedRequest(SdkHttpFullRequest signedRequest) {
        try {
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
            tokenData.put("url", signedRequest.getUri().toString());
            tokenData.put("method", signedRequest.method().name());
            tokenData.set("headers", headersArray);

            // Convert to JSON and URL-encode
            String tokenJson = objectMapper.writeValueAsString(tokenData);
            System.out.println("JSON: " + tokenJson);
            return URLEncoder.encode(tokenJson, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create token from signed request: " + e.getMessage(), e);
        }
    }
}