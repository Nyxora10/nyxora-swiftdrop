package com.nyxora.swiftdrop.auth.security;

import com.nyxora.swiftdrop.auth.config.JwtProperties;
import com.nyxora.swiftdrop.auth.entity.Merchant;
import com.nyxora.swiftdrop.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Utility component responsible for issuing, cryptographically signing, parsing,
 * and validating stateless JSON Web Tokens (JJWT 0.12.6).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    /** Claim that tells interactive user sessions apart from machine-to-machine tokens. */
    public static final String CLAIM_TOKEN_TYPE = "token_type";
    public static final String CLAIM_SCOPE = "scope";
    public static final String TOKEN_TYPE_M2M = "client_credentials";

    /** Space-separated OAuth scopes granted to M2M tokens: order creation and bulk ingestion only. */
    public static final String M2M_SCOPES = "parcels:create parcels:bulk";

    private final JwtProperties jwtProperties;

    /**
     * Resolves the HMAC-SHA256 SecretKey from configured application properties.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
        } catch (Exception e) {
            keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generates a stateless Access Token (JWT) from an authenticated UserPrincipal.
     */
    public String generateAccessToken(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return generateAccessToken(userPrincipal);
    }

    /**
     * Generates a stateless Access Token (JWT) with claims: sub, email, role, fullName.
     * When the principal carries courierId or merchantId (Task 2.5), those are embedded
     * as additional claims so the API Gateway can inject X-Courier-Id / X-Merchant-Id headers.
     */
    public String generateAccessToken(UserPrincipal userPrincipal) {
        return generateAccessToken(userPrincipal, Collections.emptyMap());
    }

    /**
     * Generates a stateless Access Token (JWT) with custom extra claims (e.g. merchantId, courierId).
     */
    public String generateAccessToken(UserPrincipal userPrincipal, Map<String, Object> extraClaims) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtProperties.getAccessTokenExpirationMs());

        String role = userPrincipal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_CUSTOMER");

        var builder = Jwts.builder()
                .subject(userPrincipal.getId().toString())
                .claim("email", userPrincipal.getEmail())
                .claim("role", role)
                .claim("fullName", userPrincipal.getFullName());

        // Task 2.5 — embed courier/merchant profile IDs for downstream header enrichment
        if (userPrincipal.getCourierId() != null) {
            builder.claim("courierId", userPrincipal.getCourierId().toString());
        }
        if (userPrincipal.getMerchantId() != null) {
            builder.claim("merchantId", userPrincipal.getMerchantId().toString());
        }

        builder.issuedAt(now).expiration(expiryDate);

        if (extraClaims != null && !extraClaims.isEmpty()) {
            extraClaims.forEach(builder::claim);
        }

        return builder.signWith(getSigningKey()).compact();
    }

    /**
     * Issues an OAuth 2.0 Machine-to-Machine (M2M) Bearer JWT for an active Merchant partner.
     * Contains standard claims plus role=ROLE_MERCHANT and merchantId, and is marked with
     * token_type=client_credentials and limited scopes so it cannot be used for interactive
     * account management (webhook config, credential rotation).
     */
    public String generateM2mToken(User user, Merchant merchant) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtProperties.getAccessTokenExpirationMs());

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", "ROLE_MERCHANT")
                .claim("merchantId", merchant.getId().toString())
                .claim("fullName", merchant.getBusinessName())
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_M2M)
                .claim(CLAIM_SCOPE, M2M_SCOPES)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Parses and extracts the Subject (User UUID) from a verified JWT.
     */
    public UUID getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return UUID.fromString(claims.getSubject());
    }

    /**
     * Parses and extracts the email claim from a verified JWT.
     */
    public String getEmailFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("email", String.class);
    }

    /**
     * Parses and extracts the authorization role claim from a verified JWT.
     */
    public String getRoleFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("role", String.class);
    }

    /**
     * True when the verified token was issued through the client_credentials grant.
     */
    public static boolean isM2mToken(Claims claims) {
        return TOKEN_TYPE_M2M.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    /**
     * Extracts all signed claims payload using the cryptographic verification key.
     */
    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Validates cryptographic signature and expiration status of a token.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.warn("[JwtTokenProvider] Expired JWT token: {}", ex.getMessage());
        } catch (JwtException | IllegalArgumentException ex) {
            log.error("[JwtTokenProvider] Invalid JWT token: {}", ex.getMessage());
        }
        return false;
    }
}

