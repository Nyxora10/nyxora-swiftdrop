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
@SuppressWarnings({"rawtypes", "unchecked"})
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
}
