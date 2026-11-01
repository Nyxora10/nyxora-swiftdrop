package com.nyxora.swiftdrop.auth.security.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.auth.dto.SocialUserProfile;
import com.nyxora.swiftdrop.auth.entity.enums.AuthProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verifier for Apple Sign In OpenID Connect (OIDC) identity tokens.
 * Validates cryptographic signatures against Apple's public RSA JWKS keys,
 * and asserts issuer, audience, and email verification claims.
 */
@Component
@Slf4j
public class AppleIdTokenVerifier {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";

    private final String appleClientId;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private final Map<String, PublicKey> keyCache = new ConcurrentHashMap<>();
    private volatile Instant lastFetchTime = Instant.EPOCH;

    public AppleIdTokenVerifier(
            @Value("${app.apple.client-id:mock-apple-client-id}") String appleClientId,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.appleClientId = appleClientId;
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * Verifies an Apple OIDC identity token and returns a normalized SocialUserProfile.
     *
     * @param identityToken Signed JWT from Apple
     * @param fullName Optional name supplied by the client on the initial authorization
     * @return Normalized SocialUserProfile
     * @throws BadCredentialsException if the token signature, claims, or verification fails
     */
    public SocialUserProfile verify(String identityToken, String fullName) {
        if (identityToken == null || identityToken.isBlank()) {
            throw new BadCredentialsException("Apple identity token is required.");
        }

        try {
            String kid = extractKeyId(identityToken);
            PublicKey publicKey = getPublicKey(kid);

            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(identityToken)
                    .getPayload();

            // 1. Verify Issuer
            if (!APPLE_ISSUER.equals(claims.getIssuer())) {
                log.warn("[AppleVerifier] Invalid issuer in Apple token: {}", claims.getIssuer());
                throw new BadCredentialsException("Apple ID token issuer is invalid.");
            }

            // 2. Verify Audience (Audience can be a string or a set in JWT)
            Set<String> audience = claims.getAudience();
            boolean audMatches = audience != null && audience.contains(appleClientId);
            if (!audMatches) {
                log.warn("[AppleVerifier] Audience mismatch in Apple token: expected {}, got {}", appleClientId, audience);
                throw new BadCredentialsException("Apple ID token audience does not match configured Client ID.");
            }

            // 3. Verify Email Verified (Handles boolean true or string "true")
            Object emailVerifiedObj = claims.get("email_verified");
            boolean emailVerified = false;
            if (emailVerifiedObj instanceof Boolean b) {
                emailVerified = b;
            } else if (emailVerifiedObj instanceof String s) {
                emailVerified = Boolean.parseBoolean(s) || "true".equalsIgnoreCase(s);
            }

            if (!emailVerified) {
                log.warn("[AppleVerifier] Apple ID token email is unverified for: {}", claims.get("email"));
                throw new BadCredentialsException("Apple ID token email is not verified.");
            }

            String email = claims.get("email", String.class);
            if (email == null || email.isBlank()) {
                log.warn("[AppleVerifier] Apple ID token contains no email claim");
                throw new BadCredentialsException("Apple ID token does not contain an email address.");
            }

            String sub = claims.getSubject();
            if (sub == null || sub.isBlank()) {
                log.warn("[AppleVerifier] Apple ID token contains no subject claim");
                throw new BadCredentialsException("Apple ID token does not contain a subject identifier.");
            }

            String resolvedFullName = (fullName != null && !fullName.isBlank())
                    ? fullName.trim()
                    : email.split("@")[0];

            return new SocialUserProfile(AuthProvider.APPLE, sub, email.toLowerCase().trim(), resolvedFullName);

        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AppleVerifier] Exception while verifying Apple identity token: {}", e.getMessage());
            throw new BadCredentialsException("Apple identity token is invalid or expired.");
        }
    }

    private String extractKeyId(String identityToken) {
        try {
            String[] parts = identityToken.split("\\.");
            if (parts.length < 2) {
                throw new BadCredentialsException("Malformed Apple identity token.");
            }
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            JsonNode headerNode = objectMapper.readTree(headerJson);
            JsonNode kidNode = headerNode.get("kid");
            if (kidNode == null || kidNode.asText().isBlank()) {
                throw new BadCredentialsException("Apple identity token header missing 'kid'.");
            }
            return kidNode.asText();
        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[AppleVerifier] Failed to extract kid from Apple token header: {}", e.getMessage());
            throw new BadCredentialsException("Failed to parse Apple identity token header.");
        }
    }

    private PublicKey getPublicKey(String kid) {
        PublicKey cached = keyCache.get(kid);
        if (cached != null && Instant.now().minusSeconds(86400).isBefore(lastFetchTime)) {
            return cached;
        }

        refreshKeys();
        PublicKey refreshed = keyCache.get(kid);
        if (refreshed == null) {
            log.warn("[AppleVerifier] Apple public key not found for kid: {}", kid);
            throw new BadCredentialsException("Apple public key not found for token signature.");
        }
        return refreshed;
    }

    private synchronized void refreshKeys() {
        try {
            log.info("[AppleVerifier] Refreshing Apple JWKS public keys from: {}", APPLE_JWKS_URL);
            AppleJwksResponse response = restClient.get()
                    .uri(APPLE_JWKS_URL)
                    .retrieve()
                    .body(AppleJwksResponse.class);

            if (response != null && response.keys() != null) {
                keyCache.clear();
                for (AppleJwkKey key : response.keys()) {
                    if ("RSA".equalsIgnoreCase(key.kty()) && key.n() != null && key.e() != null) {
                        try {
                            byte[] nBytes = Base64.getUrlDecoder().decode(key.n());
                            byte[] eBytes = Base64.getUrlDecoder().decode(key.e());
                            BigInteger modulus = new BigInteger(1, nBytes);
                            BigInteger exponent = new BigInteger(1, eBytes);
                            RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
                            KeyFactory factory = KeyFactory.getInstance("RSA");
                            keyCache.put(key.kid(), factory.generatePublic(spec));
                        } catch (Exception ex) {
                            log.warn("[AppleVerifier] Failed to parse Apple key for kid {}: {}", key.kid(), ex.getMessage());
                        }
                    }
                }
                lastFetchTime = Instant.now();
                log.info("[AppleVerifier] Successfully cached {} Apple public keys", keyCache.size());
            }
        } catch (Exception e) {
            log.error("[AppleVerifier] Failed to fetch Apple JWKS keys: {}", e.getMessage());
            throw new BadCredentialsException("Failed to fetch Apple public keys for token verification.");
        }
    }

    /**
     * Payload returned by Apple's https://appleid.apple.com/auth/keys endpoint.
     */
    public record AppleJwksResponse(List<AppleJwkKey> keys) {}
    public record AppleJwkKey(String kty, String kid, String use, String alg, String n, String e) {}
}
