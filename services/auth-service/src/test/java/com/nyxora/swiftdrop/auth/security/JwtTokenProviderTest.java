package com.nyxora.swiftdrop.auth.security;

import com.nyxora.swiftdrop.auth.config.JwtProperties;
import com.nyxora.swiftdrop.auth.entity.Role;
import com.nyxora.swiftdrop.auth.entity.User;
import com.nyxora.swiftdrop.auth.entity.enums.RoleName;
import com.nyxora.swiftdrop.auth.entity.enums.UserStatus;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtTokenProvider Unit Tests")
class JwtTokenProviderTest {

    private JwtProperties jwtProperties;
    private JwtTokenProvider jwtTokenProvider;
    private String rawSecret;
    private String base64Secret;

    @BeforeEach
    void setUp() {
        // Minimum 256-bit (32 byte) key for HMAC-SHA256
        rawSecret = "this-is-a-very-long-and-secure-secret-key-for-jwt-testing-purposes-123456789";
        base64Secret = Encoders.BASE64.encode(rawSecret.getBytes(StandardCharsets.UTF_8));

        jwtProperties = new JwtProperties();
        jwtProperties.setSecret(base64Secret);
        jwtProperties.setAccessTokenExpirationMs(3600_000L); // 1 hour

        jwtTokenProvider = new JwtTokenProvider(jwtProperties);
    }

    private User createSampleUser(RoleName roleName, UserStatus status) {
        Role role = Role.builder().id(1).name(roleName).description("Test role").build();
        return User.builder()
                .id(UUID.randomUUID())
                .email("test.user@swiftdrop.lk")
                .fullName("Test User")
                .phoneNumber("+94771234567")
                .passwordHash("hashed-password")
                .role(role)
                .status(status)
                .build();
    }

    @Test
    @DisplayName("Generate access token from UserPrincipal and validate claims")
    void generateAccessToken_FromUserPrincipal_Success() {
        User user = createSampleUser(RoleName.ROLE_CUSTOMER, UserStatus.ACTIVE);
        UserPrincipal principal = UserPrincipal.create(user);

        String token = jwtTokenProvider.generateAccessToken(principal);
        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));

        assertEquals(user.getId(), jwtTokenProvider.getUserIdFromToken(token));
        assertEquals(user.getEmail(), jwtTokenProvider.getEmailFromToken(token));
        assertEquals("ROLE_CUSTOMER", jwtTokenProvider.getRoleFromToken(token));
    }

    @Test
    @DisplayName("Generate access token with courier and merchant claims (Task 2.5)")
    void generateAccessToken_WithCourierAndMerchantClaims() {
        User user = createSampleUser(RoleName.ROLE_COURIER, UserStatus.ACTIVE);
        UUID courierId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(user, courierId, merchantId);

        String token = jwtTokenProvider.generateAccessToken(principal);
        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));

        var claims = jwtTokenProvider.getClaimsFromToken(token);
        assertEquals(courierId.toString(), claims.get("courierId", String.class));
        assertEquals(merchantId.toString(), claims.get("merchantId", String.class));
        assertEquals("ROLE_COURIER", claims.get("role", String.class));
        assertEquals("Test User", claims.get("fullName", String.class));
    }

    @Test
    @DisplayName("Generate access token from Spring Authentication object")
    void generateAccessToken_FromAuthentication_Success() {
        User user = createSampleUser(RoleName.ROLE_MERCHANT, UserStatus.ACTIVE);
        UserPrincipal principal = UserPrincipal.create(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        String token = jwtTokenProvider.generateAccessToken(auth);
        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals(user.getId(), jwtTokenProvider.getUserIdFromToken(token));
    }

    @Test
    @DisplayName("Generate access token when principal has empty authorities falls back to ROLE_CUSTOMER")
    void generateAccessToken_EmptyAuthorities_FallbackRoleCustomer() {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .email("no.roles@swiftdrop.lk")
                .fullName("No Roles")
                .authorities(Collections.emptyList())
                .build();

        String token = jwtTokenProvider.generateAccessToken(principal);
        assertNotNull(token);
        assertEquals("ROLE_CUSTOMER", jwtTokenProvider.getRoleFromToken(token));
    }

    @Test
    @DisplayName("Raw plain-text secret fallback handles non-base64 secrets cleanly")
    void getSigningKey_RawSecretFallback() {
        jwtProperties.setSecret(rawSecret); // Raw string, not Base64 encoded
        JwtTokenProvider rawProvider = new JwtTokenProvider(jwtProperties);

        User user = createSampleUser(RoleName.ROLE_CUSTOMER, UserStatus.ACTIVE);
        UserPrincipal principal = UserPrincipal.create(user);

        String token = rawProvider.generateAccessToken(principal);
        assertNotNull(token);
        assertTrue(rawProvider.validateToken(token));
    }

    @Test
    @DisplayName("Expired token returns false in validateToken")
    void validateToken_ExpiredToken_ReturnsFalse() {
        jwtProperties.setAccessTokenExpirationMs(-1000L); // Expired 1 second ago
        JwtTokenProvider expiredProvider = new JwtTokenProvider(jwtProperties);

        User user = createSampleUser(RoleName.ROLE_CUSTOMER, UserStatus.ACTIVE);
        UserPrincipal principal = UserPrincipal.create(user);
        String expiredToken = expiredProvider.generateAccessToken(principal);

        assertFalse(expiredProvider.validateToken(expiredToken));
    }

    @Test
    @DisplayName("Invalid or tampered token returns false in validateToken")
    void validateToken_TamperedToken_ReturnsFalse() {
        User user = createSampleUser(RoleName.ROLE_CUSTOMER, UserStatus.ACTIVE);
        UserPrincipal principal = UserPrincipal.create(user);
        String token = jwtTokenProvider.generateAccessToken(principal);

        String tampered = token + "xyz";
        assertFalse(jwtTokenProvider.validateToken(tampered));
        assertFalse(jwtTokenProvider.validateToken("malformed.jwt.token"));
        assertFalse(jwtTokenProvider.validateToken(null));
        assertFalse(jwtTokenProvider.validateToken(""));
    }
}
