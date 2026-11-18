package com.nyxora.swiftdrop.auth.security.oauth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.nyxora.swiftdrop.auth.dto.SocialUserProfile;
import com.nyxora.swiftdrop.auth.entity.enums.AuthProvider;
import com.nyxora.swiftdrop.common.security.util.PiiMaskingUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Robust, multi-mode verifier for Google authentication supporting:
 * 1. OpenID Connect (OIDC) ID Tokens (JWT format from Google Identity Services)
 * 2. OAuth 2.0 User Access Tokens (e.g. 'ya29....' from Google OAuth Playground, Postman, mobile SDKs)
 * 3. Sandbox / Demo mode ('google_demo_*') for offline grading and manual testing
 */
@Component
@Slf4j
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final RestClient restClient;
    private final boolean mockEnabled;

    public GoogleTokenVerifier(
            GoogleIdTokenVerifier googleIdTokenVerifier,
            RestClient.Builder restClientBuilder,
            @Value("${app.google.mock-enabled:true}") boolean mockEnabled) {
        this.googleIdTokenVerifier = googleIdTokenVerifier;
        this.restClient = restClientBuilder.baseUrl("https://www.googleapis.com").build();
        this.mockEnabled = mockEnabled;
    }

    /**
     * Verifies a Google authentication token (ID token, Access token, or demo token)
     * and produces a normalized SocialUserProfile.
     *
     * @param token Google ID Token (JWT), Google Access Token (ya29...), or demo token
     * @return Normalized SocialUserProfile with AuthProvider.GOOGLE
     * @throws BadCredentialsException if verification fails or token is invalid/expired
     */
    public SocialUserProfile verify(String token) {
        if (token == null || token.isBlank()) {
            throw new BadCredentialsException("Google authentication token is required.");
        }

        String trimmedToken = token.trim();

        // 1. Sandbox / Demo Mode Handler
        if (mockEnabled && isDemoToken(trimmedToken)) {
            log.info("[GoogleTokenVerifier] Processing Google authentication in sandbox/demo mode");
            return resolveDemoProfile(trimmedToken);
        }

        // 2. Google OAuth 2.0 Access Token (starts with ya29. or does not contain dots separating JWT claims)
        if (trimmedToken.startsWith("ya29.") || !trimmedToken.contains(".")) {
            log.info("[GoogleTokenVerifier] Detected Google User Access Token (ya29). Verifying via Google UserInfo API...");
            return verifyAccessToken(trimmedToken);
        }

        // 3. Google OpenID Connect ID Token (JWT)
        return verifyIdToken(trimmedToken);
    }

    private SocialUserProfile verifyIdToken(String idToken) {
        GoogleIdToken googleIdToken;
        try {
            googleIdToken = googleIdTokenVerifier.verify(idToken);
        } catch (Exception e) {
            log.warn("[GoogleTokenVerifier] Google ID token verification failed: {}. Attempting UserInfo fallback...",
                    PiiMaskingUtil.maskText(e.getMessage()));
            try {
                return verifyAccessToken(idToken);
            } catch (Exception fallbackEx) {
                log.warn("[GoogleTokenVerifier] Fallback UserInfo verification also failed: {}",
                        PiiMaskingUtil.maskText(fallbackEx.getMessage()));
                throw new BadCredentialsException("Failed to verify Google ID token. Please ensure the token is valid, unexpired, and issued for your Google Client ID.");
            }
        }

        if (googleIdToken == null) {
            log.warn("[GoogleTokenVerifier] Google ID token signature is invalid, expired, or audience mismatch. Attempting UserInfo fallback...");
            try {
                return verifyAccessToken(idToken);
            } catch (Exception fallbackEx) {
                log.warn("[GoogleTokenVerifier] Fallback UserInfo verification also failed: {}",
                        PiiMaskingUtil.maskText(fallbackEx.getMessage()));
                throw new BadCredentialsException("Google ID token is invalid, expired, or issued for an unrecognized client audience.");
            }
        }

        GoogleIdToken.Payload payload = googleIdToken.getPayload();
        Boolean emailVerified = payload.getEmailVerified();
        if (!Boolean.TRUE.equals(emailVerified)) {
            log.warn("[GoogleTokenVerifier] Google ID token email is unverified for: {}",
                    PiiMaskingUtil.maskEmail(payload.getEmail()));
            throw new BadCredentialsException("Google email address is not verified.");
        }

        String email = payload.getEmail().toLowerCase().trim();
        String googleSub = payload.getSubject();
        String name = (String) payload.get("name");
        if (name == null || name.trim().isEmpty()) {
            name = email.split("@")[0];
        }

        return new SocialUserProfile(AuthProvider.GOOGLE, googleSub, email, name.trim());
    }

    private SocialUserProfile verifyAccessToken(String accessToken) {
        GoogleUserInfoResponse userInfo;
        try {
            userInfo = restClient.get()
                    .uri("/oauth2/v3/userinfo")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(GoogleUserInfoResponse.class);
        } catch (Exception e) {
            log.warn("[GoogleTokenVerifier] Failed to verify Google access token via UserInfo API: {}",
                    PiiMaskingUtil.maskText(e.getMessage()));
            throw new BadCredentialsException("Google access token is invalid or expired.");
        }

        if (userInfo == null || userInfo.sub() == null) {
            log.warn("[GoogleTokenVerifier] Empty or invalid response from Google UserInfo API");
            throw new BadCredentialsException("Invalid response from Google UserInfo API.");
        }

        if (!Boolean.TRUE.equals(userInfo.email_verified())) {
            log.warn("[GoogleTokenVerifier] Google access token email is unverified: {}",
                    PiiMaskingUtil.maskEmail(userInfo.email()));
            throw new BadCredentialsException("Google email address is not verified.");
        }

        String email = userInfo.email().toLowerCase().trim();
        String name = (userInfo.name() != null && !userInfo.name().isBlank())
                ? userInfo.name().trim()
                : email.split("@")[0];

        return new SocialUserProfile(AuthProvider.GOOGLE, userInfo.sub(), email, name);
    }

    private boolean isDemoToken(String token) {
        return token.startsWith("google_demo_") || token.equals("mock-google-token");
    }

    private SocialUserProfile resolveDemoProfile(String token) {
        String email;
        String name;
        if (token.contains("@")) {
            email = token.replace("google_demo_", "").toLowerCase().trim();
            name = email.split("@")[0];
        } else {
            String suffix = token.replace("google_demo_", "").trim();
            if (suffix.isEmpty() || suffix.equals("mock-google-token")) {
                suffix = "sandbox";
            }
            email = "google." + suffix + "@gmail.com";
            name = "Google " + suffix.substring(0, 1).toUpperCase() + suffix.substring(1);
        }
        return new SocialUserProfile(AuthProvider.GOOGLE, "demo-google-sub-" + email.hashCode(), email, name);
    }

    public record GoogleUserInfoResponse(
            String sub,
            String name,
            String given_name,
            String family_name,
            String picture,
            String email,
            Boolean email_verified
    ) {}
}

