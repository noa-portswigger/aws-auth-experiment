package net.portswigger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
            String host = String.format("sts.%s.amazonaws.com", region.id());
            String baseUrl = String.format("https://%s/", host);

            SdkHttpFullRequest request = SdkHttpFullRequest.builder()
                    .method(SdkHttpMethod.POST)
                    .uri(URI.create(String.format("%s?Action=GetCallerIdentity&Version=2011-06-15", baseUrl)))
                    .putHeader("Host", host)
                    .putHeader("x-goog-cloud-target-resource", audience.replace("https://", ""))
                    .build();


            try (DefaultCredentialsProvider credentialsProvider = DefaultCredentialsProvider.builder().build()) {

                // For now, Google requires the deprecated version.
                // See https://github.com/googleapis/google-auth-library-java/issues/1792 for details.
                //noinspection deprecation
                Aws4Signer signer = Aws4Signer.create();
                Aws4SignerParams signerParams = Aws4SignerParams.builder()
                        .awsCredentials(credentialsProvider.resolveCredentials())
                        .signingName("sts")
                        .signingRegion(region)
                        .build();

                return createTokenFromSignedRequest(signer.sign(request, signerParams));
            }
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to generate subject token", e);
        }
    }

    private static String createTokenFromSignedRequest(SdkHttpFullRequest signedRequest) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            
            ArrayNode headersArray = objectMapper.createArrayNode();
            for (Map.Entry<String, List<String>> header : signedRequest.headers().entrySet()) {
                // AWS SDK may have multiple values for a header, we take the first one
                String value = header.getValue().isEmpty() ? "" : header.getValue().getFirst();
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
            
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to create token from signed request", e);
        }
    }
}