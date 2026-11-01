package com.nyxora.swiftdrop.gateway.util;

import com.nyxora.swiftdrop.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtUtil Unit Tests")
class JwtUtilTest {

    private static final String TEST_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private JwtUtil jwtUtil;
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(TEST_SECRET);
        jwtUtil = new JwtUtil(properties);
        secretKey = Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode(TEST_SECRET));
    }

    private String generateTestToken(String subject, String role, String email, long expirationMs) {
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .claim("email", email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(secretKey)
                .compact();
    }

    @Test
    @DisplayName("Valid token extracts expected claims")
    void validToken_ExtractsClaimsSuccessfully() {
        UUID userId = UUID.randomUUID();
        String token = generateTestToken(userId.toString(), "ROLE_CUSTOMER", "customer@swiftdrop.lk", 900000);

        Claims claims = jwtUtil.validateAndExtractClaims(token);

        assertNotNull(claims);
        assertEquals(userId.toString(), claims.getSubject());
        assertEquals("ROLE_CUSTOMER", claims.get("role", String.class));
        assertEquals("customer@swiftdrop.lk", claims.get("email", String.class));
    }

    @Test
    @DisplayName("Expired token throws ExpiredJwtException")
    void expiredToken_ThrowsExpiredJwtException() {
        String token = generateTestToken(UUID.randomUUID().toString(), "ROLE_MERCHANT", "merchant@shop.lk", -5000);

        assertThrows(ExpiredJwtException.class, () -> jwtUtil.validateAndExtractClaims(token));
    }

    @Test
    @DisplayName("Tampered token throws JwtException")
    void tamperedToken_ThrowsJwtException() {
        String token = generateTestToken(UUID.randomUUID().toString(), "ROLE_COURIER", "rider@swiftdrop.lk", 900000);
        String tamperedToken = token.substring(0, token.length() - 5) + "abcde";

        assertThrows(JwtException.class, () -> jwtUtil.validateAndExtractClaims(tamperedToken));
    }

    @Test
    @DisplayName("Malformed token string throws JwtException")
    void malformedToken_ThrowsJwtException() {
        assertThrows(JwtException.class, () -> jwtUtil.validateAndExtractClaims("not.a.valid.jwt.payload"));
    }
}

