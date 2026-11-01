package com.nyxora.swiftdrop.auth.security.oauth;

import com.nyxora.swiftdrop.auth.dto.SocialUserProfile;
import com.nyxora.swiftdrop.auth.entity.enums.AuthProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Facebook Graph API client for verifying access tokens and extracting user profiles.
 * Automatically computes appsecret_proof (HMAC-SHA256) to prevent cross-application
 * token spoofing attacks.
 */
@Component
@Slf4j
public class FacebookClient {

    private final String facebookAppSecret;
    private final RestClient restClient;

    public FacebookClient(
            @Value("${app.facebook.app-secret:mock-facebook-app-secret}") String facebookAppSecret,
            RestClient.Builder restClientBuilder
    ) {
        this.facebookAppSecret = facebookAppSecret;
        this.restClient = restClientBuilder.baseUrl("https://graph.facebook.com").build();
    }

    /**
     * Verifies the Facebook user access token and returns a normalized SocialUserProfile.
     *
     * @param accessToken Facebook User Access Token
     * @return Normalized SocialUserProfile
     * @throws BadCredentialsException if the token is invalid, expired, or missing email
     */
    public SocialUserProfile verify(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BadCredentialsException("Facebook access token is required.");
        }

        String appSecretProof = computeAppSecretProof(accessToken, facebookAppSecret);

        FacebookUserResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v19.0/me")
                            .queryParam("fields", "id,name,email")
                            .queryParam("access_token", accessToken)
                            .queryParam("appsecret_proof", appSecretProof)
                            .build())
                    .retrieve()
                    .body(FacebookUserResponse.class);
        } catch (Exception e) {
            log.warn("[FacebookClient] Failed to verify Facebook token: {}", e.getMessage());
            throw new BadCredentialsException("Facebook access token is invalid or expired.");
        }

        if (response == null || response.id() == null) {
            log.warn("[FacebookClient] Empty response received from Facebook Graph API");
            throw new BadCredentialsException("Facebook access token is invalid.");
        }

        if (response.email() == null || response.email().isBlank()) {
            log.warn("[FacebookClient] Facebook account has no verified email address for user id: {}", response.id());
            throw new BadCredentialsException("Facebook account does not provide an email address. Please sign in with email.");
        }

        String email = response.email().toLowerCase().trim();
        String name = (response.name() != null && !response.name().isBlank())
                ? response.name().trim()
                : email.split("@")[0];

        return new SocialUserProfile(AuthProvider.FACEBOOK, response.id(), email, name);
    }

    /**
     * Computes the HMAC-SHA256 appsecret_proof parameter required by Facebook.
     */
    public String computeAppSecretProof(String accessToken, String appSecret) {
        if (appSecret == null || appSecret.isBlank()) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(accessToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(rawHmac.length * 2);
            for (byte b : rawHmac) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            log.error("[FacebookClient] Failed to compute appsecret_proof: {}", e.getMessage());
            throw new BadCredentialsException("Failed to compute Facebook security proof.");
        }
    }

    /**
     * Response payload returned by Facebook Graph API /me endpoint.
     */
    public record FacebookUserResponse(String id, String name, String email) {}
}
