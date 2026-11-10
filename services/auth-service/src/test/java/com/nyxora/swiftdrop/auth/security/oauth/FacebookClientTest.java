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

    private static final String APP_SECRET = "fb_test_app_secret_123456";

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private FacebookClient facebookClient;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);

        facebookClient = new FacebookClient(APP_SECRET, restClientBuilder);
    }

    @Test
    @DisplayName("Blank or null token throws BadCredentialsException")
    void verify_BlankOrNullToken_ThrowsException() {
        assertThrows(BadCredentialsException.class, () -> facebookClient.verify(""));
        assertThrows(BadCredentialsException.class, () -> facebookClient.verify("   "));
        assertThrows(BadCredentialsException.class, () -> facebookClient.verify(null));
    }

    @Test
    @DisplayName("computeAppSecretProof returns empty string when appSecret is null or blank")
    void computeAppSecretProof_BlankOrNullSecret_ReturnsEmpty() {
        assertEquals("", facebookClient.computeAppSecretProof("access-token", null));
        assertEquals("", facebookClient.computeAppSecretProof("access-token", ""));
        assertEquals("", facebookClient.computeAppSecretProof("access-token", "   "));
    }

    @Test
    @DisplayName("computeAppSecretProof computes valid hex-encoded HMAC-SHA256")
    void computeAppSecretProof_ValidInput_ReturnsHexHmac() {
        String proof = facebookClient.computeAppSecretProof("EAAB12345", "test-secret");
        assertNotNull(proof);
        assertFalse(proof.isBlank());
        assertEquals(64, proof.length()); // SHA-256 produces 64 hex characters (32 bytes * 2)
    }

    @Test
    @DisplayName("Valid Facebook response with name returns normalized SocialUserProfile")
    @SuppressWarnings("unchecked")
    void verify_ValidToken_ReturnsSocialUserProfile() {
        FacebookClient.FacebookUserResponse mockResponse =
                new FacebookClient.FacebookUserResponse("fb-user-999", "Kamal Perera", "kamal.perera@facebook.com");

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(FacebookClient.FacebookUserResponse.class)).thenReturn(mockResponse);

        SocialUserProfile profile = facebookClient.verify("valid-facebook-token");

        assertNotNull(profile);
        assertEquals(AuthProvider.FACEBOOK, profile.provider());
        assertEquals("fb-user-999", profile.providerId());
        assertEquals("kamal.perera@facebook.com", profile.email());
        assertEquals("Kamal Perera", profile.fullName());
    }

    @Test
    @DisplayName("Valid Facebook response without name defaults name to email prefix")
    @SuppressWarnings("unchecked")
    void verify_ValidToken_NullName_DefaultsToEmailPrefix() {
        FacebookClient.FacebookUserResponse mockResponse =
                new FacebookClient.FacebookUserResponse("fb-user-888", null, "sunil.fernando@facebook.com");

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(FacebookClient.FacebookUserResponse.class)).thenReturn(mockResponse);

        SocialUserProfile profile = facebookClient.verify("valid-facebook-token");

        assertNotNull(profile);
        assertEquals("sunil.fernando", profile.fullName());
    }

    @Test
    @DisplayName("Valid Facebook response with blank name defaults name to email prefix")
    @SuppressWarnings("unchecked")
    void verify_ValidToken_BlankName_DefaultsToEmailPrefix() {
        FacebookClient.FacebookUserResponse mockResponse =
                new FacebookClient.FacebookUserResponse("fb-user-777", "   ", "dilani@facebook.com");

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(FacebookClient.FacebookUserResponse.class)).thenReturn(mockResponse);

        SocialUserProfile profile = facebookClient.verify("valid-facebook-token");

        assertNotNull(profile);
        assertEquals("dilani", profile.fullName());
    }

    @Test
    @DisplayName("Facebook Graph API call throwing exception throws BadCredentialsException")
    @SuppressWarnings("unchecked")
    void verify_NetworkException_ThrowsBadCredentialsException() {
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenThrow(new RuntimeException("Graph API down"));

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> facebookClient.verify("bad-token"));
        assertTrue(ex.getMessage().contains("invalid or expired"));
    }

    @Test
    @DisplayName("Empty or null ID in Facebook response throws BadCredentialsException")
    @SuppressWarnings("unchecked")
    void verify_NullResponseOrId_ThrowsBadCredentialsException() {
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(FacebookClient.FacebookUserResponse.class))
                .thenReturn(new FacebookClient.FacebookUserResponse(null, "Name", "email@fb.com"));

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> facebookClient.verify("bad-token"));
        assertTrue(ex.getMessage().contains("access token is invalid"));
    }

    @Test
    @DisplayName("Null or blank email in Facebook response throws BadCredentialsException")
    @SuppressWarnings("unchecked")
    void verify_NullOrBlankEmail_ThrowsBadCredentialsException() {
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(FacebookClient.FacebookUserResponse.class))
                .thenReturn(new FacebookClient.FacebookUserResponse("fb-123", "Name", null));

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> facebookClient.verify("no-email-token"));
        assertTrue(ex.getMessage().contains("does not provide an email address"));

        when(responseSpec.body(FacebookClient.FacebookUserResponse.class))
                .thenReturn(new FacebookClient.FacebookUserResponse("fb-123", "Name", "   "));

        assertThrows(BadCredentialsException.class, () -> facebookClient.verify("blank-email-token"));
    }
}
