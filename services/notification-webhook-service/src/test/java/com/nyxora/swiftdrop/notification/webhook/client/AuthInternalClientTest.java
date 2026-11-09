package com.nyxora.swiftdrop.notification.webhook.client;

import com.nyxora.swiftdrop.notification.webhook.client.dto.MerchantWebhookConfigResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthInternalClient Unit Tests")
class AuthInternalClientTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private RestClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private AuthInternalClient authClient;

    private UUID merchantId;

    @BeforeEach
    void setUp() {
        authClient = new AuthInternalClient(restClient);
        merchantId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Should return empty Optional when merchantId is null")
    void shouldReturnEmptyWhenMerchantIdIsNull() {
        Optional<MerchantWebhookConfigResponse> result = authClient.getMerchantWebhookConfig(null);
        assertTrue(result.isEmpty());
        verifyNoInteractions(restClient);
    }

    @Test
    @DisplayName("Should successfully return active config when auth-service returns 200 with valid body")
    void shouldReturnConfigWhenValid() {
        MerchantWebhookConfigResponse expectedResponse = new MerchantWebhookConfigResponse(
                merchantId,
                "https://example.com/webhook",
                "whsec_test_secret_1234567890abcdef",
                true
        );

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), eq(merchantId))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.body(MerchantWebhookConfigResponse.class)).thenReturn(expectedResponse);

        Optional<MerchantWebhookConfigResponse> result = authClient.getMerchantWebhookConfig(merchantId);

        assertTrue(result.isPresent());
        assertEquals(expectedResponse, result.get());
        assertTrue(result.get().isEligibleForDispatch());
    }

    @Test
    @DisplayName("Should return empty Optional when merchant config is inactive")
    void shouldReturnEmptyWhenInactive() {
        MerchantWebhookConfigResponse inactiveResponse = new MerchantWebhookConfigResponse(
                merchantId,
                "https://example.com/webhook",
                "whsec_test_secret_1234567890abcdef",
                false
        );

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), eq(merchantId))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.body(MerchantWebhookConfigResponse.class)).thenReturn(inactiveResponse);

        Optional<MerchantWebhookConfigResponse> result = authClient.getMerchantWebhookConfig(merchantId);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty Optional when body is null")
    void shouldReturnEmptyWhenBodyIsNull() {
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), eq(merchantId))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.body(MerchantWebhookConfigResponse.class)).thenReturn(null);

        Optional<MerchantWebhookConfigResponse> result = authClient.getMerchantWebhookConfig(merchantId);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should instantiate via Spring Autowired constructor with fallback builder")
    void shouldInstantiateWithFallbackBuilder() {
        AuthInternalClient client = new AuthInternalClient(null, "http://localhost:8081");
        assertNotNull(client);
    }
}

