package com.nyxora.swiftdrop.auth.security.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.auth.dto.SocialUserProfile;
import com.nyxora.swiftdrop.auth.entity.enums.AuthProvider;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AppleIdTokenVerifier Unit Tests")
class AppleIdTokenVerifierTest {

    private static final String CLIENT_ID = "com.swiftdrop.client";

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private ObjectMapper objectMapper;
    private KeyPair keyPair;
    private String kid;
    private AppleIdTokenVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();

        // Generate RSA KeyPair for valid signature verification
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        keyPair = kpg.generateKeyPair();
        kid = "apple-key-id-1";

        when(restClientBuilder.build()).thenReturn(restClient);

        verifier = new AppleIdTokenVerifier(CLIENT_ID, true, restClientBuilder, objectMapper);
    }

    private void mockAppleJwksResponse(List<AppleIdTokenVerifier.AppleJwkKey> keys) {
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(AppleIdTokenVerifier.AppleJwksResponse.class))
                .thenReturn(new AppleIdTokenVerifier.AppleJwksResponse(keys));
    }

    private AppleIdTokenVerifier.AppleJwkKey createJwkKey(String keyId, RSAPublicKey pubKey) {
        String n = Base64.getUrlEncoder().withoutPadding().encodeToString(pubKey.getModulus().toByteArray());
        String e = Base64.getUrlEncoder().withoutPadding().encodeToString(pubKey.getPublicExponent().toByteArray());
        return new AppleIdTokenVerifier.AppleJwkKey("RSA", keyId, "sig", "RS256", n, e);
    }

    private String buildJwt(String keyId, String issuer, String audience, String subject,
                             String email, Object emailVerified, Date expiration) {
        return Jwts.builder()
                .header()
                .keyId(keyId)
                .and()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject(subject)
                .claim("email", email)
                .claim("email_verified", emailVerified)
                .expiration(expiration)
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    @Test
    @DisplayName("Blank or null token throws BadCredentialsException")
    void verify_BlankOrNullToken_ThrowsException() {
        assertThrows(BadCredentialsException.class, () -> verifier.verify("", "Name"));
        assertThrows(BadCredentialsException.class, () -> verifier.verify("   ", "Name"));
        assertThrows(BadCredentialsException.class, () -> verifier.verify(null, "Name"));
    }

    @Test
    @DisplayName("Sandbox demo token with email resolves SocialUserProfile")
    void verify_DemoToken_WithEmail_ResolvesProfile() {
        SocialUserProfile profile = verifier.verify("apple_demo_chamath@icloud.com", "Chamath Apple");
        assertNotNull(profile);
        assertEquals(AuthProvider.APPLE, profile.provider());
        assertEquals("chamath@icloud.com", profile.email());
        assertEquals("Chamath Apple", profile.fullName());
        assertTrue(profile.providerId().startsWith("apple_sub_"));
    }

    @Test
    @DisplayName("Sandbox demo token with email without fullName defaults to email prefix")
    void verify_DemoToken_WithoutFullName_UsesEmailPrefix() {
        SocialUserProfile profile = verifier.verify("apple_demo_sarath@icloud.com", null);
        assertNotNull(profile);
        assertEquals("sarath@icloud.com", profile.email());
        assertEquals("sarath", profile.fullName());
    }

    @Test
    @DisplayName("Sandbox demo token without email defaults to customer.apple@swiftdrop.lk")
    void verify_DemoToken_WithoutEmail_UsesDefaults() {
        SocialUserProfile profile = verifier.verify("apple_demo_token", null);
        assertNotNull(profile);
        assertEquals("customer.apple@swiftdrop.lk", profile.email());
        assertEquals("customer.apple", profile.fullName());
        assertEquals("apple_sub_99999", profile.providerId());

        SocialUserProfile profile2 = verifier.verify("demo_apple", "Explicit Name");
        assertEquals("Explicit Name", profile2.fullName());
    }

    @Test
    @DisplayName("Valid Apple ID Token with Boolean email_verified successfully verifies")
    void verify_ValidToken_BooleanVerified_Success() {
        RSAPublicKey pubKey = (RSAPublicKey) keyPair.getPublic();
        mockAppleJwksResponse(List.of(createJwkKey(kid, pubKey)));

        String token = buildJwt(
                kid,
                "https://appleid.apple.com",
                CLIENT_ID,
                "apple-sub-001",
                "user@privaterelay.appleid.com",
                true,
                new Date(System.currentTimeMillis() + 3600_000)
        );

        SocialUserProfile profile = verifier.verify(token, "John Doe");
        assertNotNull(profile);
        assertEquals(AuthProvider.APPLE, profile.provider());
        assertEquals("apple-sub-001", profile.providerId());
        assertEquals("user@privaterelay.appleid.com", profile.email());
        assertEquals("John Doe", profile.fullName());
    }

    @Test
    @DisplayName("Valid Apple ID Token with String email_verified ('true') successfully verifies")
    void verify_ValidToken_StringVerified_Success() {
        RSAPublicKey pubKey = (RSAPublicKey) keyPair.getPublic();
        mockAppleJwksResponse(List.of(createJwkKey(kid, pubKey)));

        String token = buildJwt(
                kid,
                "https://appleid.apple.com",
                CLIENT_ID,
                "apple-sub-002",
                "sarah@privaterelay.appleid.com",
                "true",
                new Date(System.currentTimeMillis() + 3600_000)
        );

        SocialUserProfile profile = verifier.verify(token, null);
        assertNotNull(profile);
        assertEquals("sarah", profile.fullName());
    }

    @Test
    @DisplayName("Invalid issuer in Apple token throws BadCredentialsException")
    void verify_InvalidIssuer_ThrowsException() {
        RSAPublicKey pubKey = (RSAPublicKey) keyPair.getPublic();
        mockAppleJwksResponse(List.of(createJwkKey(kid, pubKey)));

        String token = buildJwt(
                kid,
                "https://invalid.issuer.com",
                CLIENT_ID,
                "apple-sub-001",
                "user@apple.com",
                true,
                new Date(System.currentTimeMillis() + 3600_000)
        );

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> verifier.verify(token, null));
        assertTrue(ex.getMessage().contains("issuer is invalid"));
    }

    @Test
    @DisplayName("Audience mismatch in Apple token throws BadCredentialsException")
    void verify_AudienceMismatch_ThrowsException() {
        RSAPublicKey pubKey = (RSAPublicKey) keyPair.getPublic();
        mockAppleJwksResponse(List.of(createJwkKey(kid, pubKey)));

        String token = buildJwt(
                kid,
                "https://appleid.apple.com",
                "wrong.client.id",
                "apple-sub-001",
                "user@apple.com",
                true,
                new Date(System.currentTimeMillis() + 3600_000)
        );

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> verifier.verify(token, null));
        assertTrue(ex.getMessage().contains("audience does not match"));
    }

    @Test
    @DisplayName("Unverified email in Apple token throws BadCredentialsException")
    void verify_UnverifiedEmail_ThrowsException() {
        RSAPublicKey pubKey = (RSAPublicKey) keyPair.getPublic();
        mockAppleJwksResponse(List.of(createJwkKey(kid, pubKey)));

        String token = buildJwt(
                kid,
                "https://appleid.apple.com",
                CLIENT_ID,
                "apple-sub-001",
                "user@apple.com",
                false,
                new Date(System.currentTimeMillis() + 3600_000)
        );

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> verifier.verify(token, null));
        assertTrue(ex.getMessage().contains("email is not verified"));
    }

    @Test
    @DisplayName("Missing email claim in Apple token throws BadCredentialsException")
    void verify_MissingEmail_ThrowsException() {
        RSAPublicKey pubKey = (RSAPublicKey) keyPair.getPublic();
        mockAppleJwksResponse(List.of(createJwkKey(kid, pubKey)));

        String token = Jwts.builder()
                .header().keyId(kid).and()
                .issuer("https://appleid.apple.com")
                .audience().add(CLIENT_ID).and()
                .subject("apple-sub-001")
                .claim("email_verified", true)
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> verifier.verify(token, null));
        assertTrue(ex.getMessage().contains("does not contain an email address"));
    }

    @Test
    @DisplayName("Missing subject identifier in Apple token throws BadCredentialsException")
    void verify_MissingSubject_ThrowsException() {
        RSAPublicKey pubKey = (RSAPublicKey) keyPair.getPublic();
        mockAppleJwksResponse(List.of(createJwkKey(kid, pubKey)));

        String token = Jwts.builder()
                .header().keyId(kid).and()
                .issuer("https://appleid.apple.com")
                .audience().add(CLIENT_ID).and()
                .claim("email", "user@apple.com")
                .claim("email_verified", true)
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> verifier.verify(token, null));
        assertTrue(ex.getMessage().contains("does not contain a subject identifier"));
    }

    @Test
    @DisplayName("Malformed token without dots throws BadCredentialsException")
    void verify_MalformedToken_ThrowsException() {
        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> verifier.verify("no-dots-in-this-token", null));
        assertTrue(ex.getMessage().contains("Malformed Apple identity token"));
    }

    @Test
    @DisplayName("Header without kid throws BadCredentialsException")
    void verify_HeaderWithoutKid_ThrowsException() {
        String tokenNoKid = Jwts.builder()
                .issuer("https://appleid.apple.com")
                .audience().add(CLIENT_ID).and()
                .subject("apple-sub-001")
                .claim("email", "user@apple.com")
                .claim("email_verified", true)
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> verifier.verify(tokenNoKid, null));
        assertTrue(ex.getMessage().contains("missing 'kid'"));
    }

    @Test
    @DisplayName("Header with invalid base64 throws BadCredentialsException")
    void verify_InvalidBase64Header_ThrowsException() {
        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> verifier.verify("invalid!!base64.payload.sig", null));
        assertTrue(ex.getMessage().contains("Failed to parse Apple identity token header"));
    }

    @Test
    @DisplayName("Key not found in Apple JWKS throws BadCredentialsException")
    void verify_KeyNotFoundInJwks_ThrowsException() {
        mockAppleJwksResponse(List.of()); // Empty keys

        String token = buildJwt(
                "unknown-kid",
                "https://appleid.apple.com",
                CLIENT_ID,
                "apple-sub-001",
                "user@apple.com",
                true,
                new Date(System.currentTimeMillis() + 3600_000)
        );

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> verifier.verify(token, null));
        assertTrue(ex.getMessage().contains("Apple public key not found"));
    }

    @Test
    @DisplayName("JWKS fetch exception throws BadCredentialsException")
    void verify_JwksFetchException_ThrowsException() {
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenThrow(new RuntimeException("Network down"));

        String token = buildJwt(
                kid,
                "https://appleid.apple.com",
                CLIENT_ID,
                "apple-sub-001",
                "user@apple.com",
                true,
                new Date(System.currentTimeMillis() + 3600_000)
        );

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> verifier.verify(token, null));
        assertTrue(ex.getMessage().contains("Failed to fetch Apple public keys"));
    }

    @Test
    @DisplayName("Token signed with different key throws BadCredentialsException")
    void verify_WrongSignature_ThrowsException() throws Exception {
        RSAPublicKey pubKey = (RSAPublicKey) keyPair.getPublic();
        mockAppleJwksResponse(List.of(createJwkKey(kid, pubKey)));

        // Sign with different key
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair otherKeyPair = kpg.generateKeyPair();

        String token = Jwts.builder()
                .header().keyId(kid).and()
                .issuer("https://appleid.apple.com")
                .audience().add(CLIENT_ID).and()
                .subject("apple-sub-001")
                .claim("email", "user@apple.com")
                .claim("email_verified", true)
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(otherKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> verifier.verify(token, null));
        assertTrue(ex.getMessage().contains("invalid or expired"));
    }

    @Test
    @DisplayName("JWKS response with non-RSA key or malformed bytes is safely handled")
    void verify_JwksWithInvalidKeys_HandledGracefully() {
        AppleIdTokenVerifier.AppleJwkKey invalidKty =
                new AppleIdTokenVerifier.AppleJwkKey("EC", "ec-key", "sig", "ES256", "n", "e");
        AppleIdTokenVerifier.AppleJwkKey badBase64 =
                new AppleIdTokenVerifier.AppleJwkKey("RSA", "bad-key", "sig", "RS256", "invalid!!b64", "e");

        mockAppleJwksResponse(List.of(invalidKty, badBase64));

        String token = buildJwt(
                "any-key",
                "https://appleid.apple.com",
                CLIENT_ID,
                "sub-1",
                "user@apple.com",
                true,
                new Date(System.currentTimeMillis() + 3600_000)
        );

        assertThrows(BadCredentialsException.class, () -> verifier.verify(token, null));
    }
}

