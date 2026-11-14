package com.nyxora.swiftdrop.auth.security.oauth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.nyxora.swiftdrop.auth.dto.SocialUserProfile;
import com.nyxora.swiftdrop.auth.entity.enums.AuthProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GoogleTokenVerifier Unit Tests")
class GoogleTokenVerifierTest {

    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private RestClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private GoogleTokenVerifier googleTokenVerifier;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);

        googleTokenVerifier = new GoogleTokenVerifier(googleIdTokenVerifier, restClientBuilder, true);
    }

    @Test
    @DisplayName("Blank or null token throws BadCredentialsException")
    void verify_BlankToken_ThrowsBadCredentialsException() {
        assertThrows(BadCredentialsException.class, () -> googleTokenVerifier.verify(""));
        assertThrows(BadCredentialsException.class, () -> googleTokenVerifier.verify(null));
    }

    @Test
    @DisplayName("Sandbox demo token resolves demo user profile")
    void verify_DemoToken_ResolvesDemoProfile() {
        SocialUserProfile profile = googleTokenVerifier.verify("google_demo_kamal@gmail.com");

        assertNotNull(profile);
        assertEquals(AuthProvider.GOOGLE, profile.provider());
        assertEquals("kamal@gmail.com", profile.email());
        assertEquals("kamal", profile.fullName());
    }

    @Test
    @DisplayName("Valid Google ID token (JWT) resolves verified user profile")
    void verify_ValidIdToken_ReturnsSocialUserProfile() throws Exception {
        String mockJwt = "header.payload.signature";
        GoogleIdToken googleIdToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail("verified.user@gmail.com");
        payload.setEmailVerified(true);
        payload.setSubject("google-sub-98765");
        payload.set("name", "Verified User");

        when(googleIdToken.getPayload()).thenReturn(payload);
        when(googleIdTokenVerifier.verify(mockJwt)).thenReturn(googleIdToken);

        SocialUserProfile profile = googleTokenVerifier.verify(mockJwt);

        assertNotNull(profile);
        assertEquals(AuthProvider.GOOGLE, profile.provider());
        assertEquals("google-sub-98765", profile.providerId());
        assertEquals("verified.user@gmail.com", profile.email());
        assertEquals("Verified User", profile.fullName());
    }

    @Test
    @DisplayName("Valid Google Access token (ya29...) verifies via UserInfo endpoint")
    @SuppressWarnings("unchecked")
    void verify_ValidAccessToken_ReturnsSocialUserProfile() {
        String mockAccessToken = "ya29.a0AehfTOnCKKkeYhyHN...";
        GoogleTokenVerifier.GoogleUserInfoResponse mockResponse =
                new GoogleTokenVerifier.GoogleUserInfoResponse(
                        "google-sub-11223", "Chamath Silva", "Chamath", "Silva",
                        "https://photo.url", "chamath@gmail.com", true
                );

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/oauth2/v3/userinfo")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(eq("Authorization"), eq("Bearer " + mockAccessToken))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(GoogleTokenVerifier.GoogleUserInfoResponse.class)).thenReturn(mockResponse);

        SocialUserProfile profile = googleTokenVerifier.verify(mockAccessToken);

        assertNotNull(profile);
        assertEquals(AuthProvider.GOOGLE, profile.provider());
        assertEquals("google-sub-11223", profile.providerId());
        assertEquals("chamath@gmail.com", profile.email());
        assertEquals("Chamath Silva", profile.fullName());
    }

    @Test
    @DisplayName("Google ID token with unverified email throws BadCredentialsException")
    void verify_UnverifiedEmail_ThrowsBadCredentialsException() throws Exception {
        String mockJwt = "header.payload.signature";
        GoogleIdToken googleIdToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail("unverified.user@gmail.com");
        payload.setEmailVerified(false);
        payload.setSubject("google-sub-11111");

        when(googleIdToken.getPayload()).thenReturn(payload);
        when(googleIdTokenVerifier.verify(mockJwt)).thenReturn(googleIdToken);

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> googleTokenVerifier.verify(mockJwt));
        assertTrue(ex.getMessage().contains("not verified"));
    }

    @Test
    @DisplayName("Sandbox demo token without email resolves default sandbox profile")
    void verify_DemoToken_WithoutEmail_ResolvesProfile() {
        SocialUserProfile profile = googleTokenVerifier.verify("google_demo_testing");
        assertNotNull(profile);
        assertEquals("google.testing@gmail.com", profile.email());
        assertEquals("Google Testing", profile.fullName());

        SocialUserProfile profileMock = googleTokenVerifier.verify("mock-google-token");
        assertNotNull(profileMock);
        assertEquals("google.sandbox@gmail.com", profileMock.email());
        assertEquals("Google Sandbox", profileMock.fullName());

        SocialUserProfile profileEmptySuffix = googleTokenVerifier.verify("google_demo_");
        assertNotNull(profileEmptySuffix);
        assertEquals("google.sandbox@gmail.com", profileEmptySuffix.email());
    }

    @Test
    @DisplayName("Google ID token without name claim defaults to email prefix")
    void verify_IdToken_WithoutName_DefaultsToEmailPrefix() throws Exception {
        String mockJwt = "header.payload.signature";
        GoogleIdToken googleIdToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail("kasun.perera@gmail.com");
        payload.setEmailVerified(true);
        payload.setSubject("google-sub-22222");

        when(googleIdToken.getPayload()).thenReturn(payload);
        when(googleIdTokenVerifier.verify(mockJwt)).thenReturn(googleIdToken);

        SocialUserProfile profile = googleTokenVerifier.verify(mockJwt);
        assertNotNull(profile);
        assertEquals("kasun.perera", profile.fullName());
    }

    @Test
    @DisplayName("Google ID token verify throws exception, fallback to verifyAccessToken succeeds")
    void verify_IdTokenException_FallbackAccessTokenSucceeds() throws Exception {
        String mockJwt = "header.payload.signature";
        when(googleIdTokenVerifier.verify(mockJwt)).thenThrow(new RuntimeException("Crypto parse error"));

        GoogleTokenVerifier.GoogleUserInfoResponse mockResponse =
                new GoogleTokenVerifier.GoogleUserInfoResponse(
                        "google-sub-33333", null, "First", "Last",
                        null, "fallback@gmail.com", true
                );

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/oauth2/v3/userinfo")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(eq("Authorization"), eq("Bearer " + mockJwt))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(GoogleTokenVerifier.GoogleUserInfoResponse.class)).thenReturn(mockResponse);

        SocialUserProfile profile = googleTokenVerifier.verify(mockJwt);
        assertNotNull(profile);
        assertEquals("fallback@gmail.com", profile.email());
        assertEquals("fallback", profile.fullName());
    }

    @Test
    @DisplayName("Google ID token verify throws exception and fallback also fails, throws BadCredentialsException")
    void verify_IdTokenException_FallbackFails_ThrowsException() throws Exception {
        String mockJwt = "header.payload.signature";
        when(googleIdTokenVerifier.verify(mockJwt)).thenThrow(new RuntimeException("Crypto parse error"));

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/oauth2/v3/userinfo")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(eq("Authorization"), eq("Bearer " + mockJwt))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenThrow(new RuntimeException("API down"));

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> googleTokenVerifier.verify(mockJwt));
        assertTrue(ex.getMessage().contains("Failed to verify Google ID token"));
    }

    @Test
    @DisplayName("Google ID token null return, fallback to verifyAccessToken succeeds")
    void verify_IdTokenNull_FallbackAccessTokenSucceeds() throws Exception {
        String mockJwt = "header.payload.signature";
        when(googleIdTokenVerifier.verify(mockJwt)).thenReturn(null);

        GoogleTokenVerifier.GoogleUserInfoResponse mockResponse =
                new GoogleTokenVerifier.GoogleUserInfoResponse(
                        "google-sub-44444", "John Doe", "John", "Doe",
                        null, "johndoe@gmail.com", true
                );

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/oauth2/v3/userinfo")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(eq("Authorization"), eq("Bearer " + mockJwt))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(GoogleTokenVerifier.GoogleUserInfoResponse.class)).thenReturn(mockResponse);

        SocialUserProfile profile = googleTokenVerifier.verify(mockJwt);
        assertNotNull(profile);
        assertEquals("johndoe@gmail.com", profile.email());
        assertEquals("John Doe", profile.fullName());
    }

    @Test
    @DisplayName("Google ID token null return and fallback fails, throws BadCredentialsException")
    void verify_IdTokenNull_FallbackFails_ThrowsException() throws Exception {
        String mockJwt = "header.payload.signature";
        when(googleIdTokenVerifier.verify(mockJwt)).thenReturn(null);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/oauth2/v3/userinfo")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(eq("Authorization"), eq("Bearer " + mockJwt))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenThrow(new RuntimeException("API down"));

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> googleTokenVerifier.verify(mockJwt));
        assertTrue(ex.getMessage().contains("Google ID token is invalid, expired, or issued for an unrecognized client audience"));
    }

    @Test
    @DisplayName("Google Access token API call throwing exception throws BadCredentialsException")
    void verify_AccessToken_NetworkError_ThrowsException() {
        String mockAccessToken = "ya29.network.error";
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/oauth2/v3/userinfo")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(eq("Authorization"), eq("Bearer " + mockAccessToken))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenThrow(new RuntimeException("Timeout"));

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> googleTokenVerifier.verify(mockAccessToken));
        assertTrue(ex.getMessage().contains("Google access token is invalid or expired"));
    }

    @Test
    @DisplayName("Google Access token null response or sub throws BadCredentialsException")
    void verify_AccessToken_NullResponseOrSub_ThrowsException() {
        String mockAccessToken = "ya29.null.response";
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/oauth2/v3/userinfo")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(eq("Authorization"), eq("Bearer " + mockAccessToken))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(GoogleTokenVerifier.GoogleUserInfoResponse.class)).thenReturn(null);

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> googleTokenVerifier.verify(mockAccessToken));
        assertTrue(ex.getMessage().contains("Invalid response from Google UserInfo API"));

        // Null sub
        when(responseSpec.body(GoogleTokenVerifier.GoogleUserInfoResponse.class))
                .thenReturn(new GoogleTokenVerifier.GoogleUserInfoResponse(null, "Name", null, null, null, "email@gmail.com", true));
        assertThrows(BadCredentialsException.class, () -> googleTokenVerifier.verify(mockAccessToken));
    }

    @Test
    @DisplayName("Google Access token unverified email throws BadCredentialsException")
    void verify_AccessToken_UnverifiedEmail_ThrowsException() {
        String mockAccessToken = "ya29.unverified.email";
        GoogleTokenVerifier.GoogleUserInfoResponse mockResponse =
                new GoogleTokenVerifier.GoogleUserInfoResponse(
                        "sub-99", "Name", null, null, null, "unverified@gmail.com", false
                );

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/oauth2/v3/userinfo")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(eq("Authorization"), eq("Bearer " + mockAccessToken))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(GoogleTokenVerifier.GoogleUserInfoResponse.class)).thenReturn(mockResponse);

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> googleTokenVerifier.verify(mockAccessToken));
        assertTrue(ex.getMessage().contains("Google email address is not verified"));
    }
}
