package com.nyxora.swiftdrop.auth.security.oauth;

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

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FacebookClient Unit Tests")
class FacebookClientTest {

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private FacebookClient facebookClient;

    private final String appSecret = "test-facebook-secret-123";

    @BeforeEach
    void setUp() {
        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);

        facebookClient = new FacebookClient(appSecret, restClientBuilder);
    }

    @Test
    @DisplayName("Valid Facebook token returns normalized SocialUserProfile")
    @SuppressWarnings("unchecked")
    void verify_ValidToken_ReturnsSocialUserProfile() {
        String token = "valid-user-access-token";
        FacebookClient.FacebookUserResponse mockResponse =
                new FacebookClient.FacebookUserResponse("fb-user-12345", "Kamal Silva", "kamal@facebook.lk");

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(FacebookClient.FacebookUserResponse.class)).thenReturn(mockResponse);

        SocialUserProfile profile = facebookClient.verify(token);

        assertNotNull(profile);
        assertEquals(AuthProvider.FACEBOOK, profile.provider());
        assertEquals("fb-user-12345", profile.providerId());
        assertEquals("kamal@facebook.lk", profile.email());
        assertEquals("Kamal Silva", profile.fullName());
    }

    @Test
    @DisplayName("Facebook account without email throws BadCredentialsException")
    @SuppressWarnings("unchecked")
    void verify_MissingEmail_ThrowsBadCredentialsException() {
        String token = "no-email-token";
        FacebookClient.FacebookUserResponse mockResponse =
                new FacebookClient.FacebookUserResponse("fb-user-999", "No Email User", null);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(FacebookClient.FacebookUserResponse.class)).thenReturn(mockResponse);

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> facebookClient.verify(token));
        assertTrue(ex.getMessage().contains("does not provide an email address"));
    }

    @Test
    @DisplayName("Facebook Graph API call failure throws BadCredentialsException")
    @SuppressWarnings("unchecked")
    void verify_GraphApiError_ThrowsBadCredentialsException() {
        String token = "invalid-token";

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenThrow(new RuntimeException("Connection timeout"));

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> facebookClient.verify(token));
        assertTrue(ex.getMessage().contains("invalid or expired"));
    }

    @Test
    @DisplayName("Blank or null access token throws BadCredentialsException")
    void verify_BlankToken_ThrowsBadCredentialsException() {
        assertThrows(BadCredentialsException.class, () -> facebookClient.verify(""));
        assertThrows(BadCredentialsException.class, () -> facebookClient.verify(null));
    }

    @Test
    @DisplayName("HMAC-SHA256 appsecret_proof produces 64-character hex string")
    void computeAppSecretProof_ComputesHmacCorrectly() {
        String proof = facebookClient.computeAppSecretProof("access_token_xyz", appSecret);

        assertNotNull(proof);
        assertEquals(64, proof.length()); // SHA-256 produces 256 bits = 32 bytes = 64 hex chars
    }
}
