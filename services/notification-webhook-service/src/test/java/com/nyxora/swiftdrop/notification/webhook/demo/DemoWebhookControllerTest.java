package com.nyxora.swiftdrop.notification.webhook.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.notification.webhook.client.AuthInternalClient;
import com.nyxora.swiftdrop.notification.webhook.client.dto.MerchantWebhookConfigResponse;
import com.nyxora.swiftdrop.notification.webhook.demo.dto.DemoWebhookEvent;
import com.nyxora.swiftdrop.notification.webhook.util.HmacSignatureUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DemoWebhookController & EventStore Security Unit Tests")
class DemoWebhookControllerTest {

    private static final String DEMO_RECEIVER_URL = "/api/v1/webhooks/demo-receiver";
    private static final String DEMO_EVENTS_URL = "/api/v1/webhooks/demo-events";
    private static final String TEST_SECRET = "whsec_test_secret_key_1234567890abcdef";

    @Mock
    private AuthInternalClient authInternalClient;

    @Mock
    private Environment environment;

    private DemoWebhookEventStore eventStore;
    private DemoWebhookController controller;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID userId;
    private UUID merchantId;
    private String samplePayload;
    private String validSignature;

    @BeforeEach
    void setUp() {
        eventStore = new DemoWebhookEventStore();
        objectMapper = new ObjectMapper();

        userId = UUID.randomUUID();
        merchantId = UUID.randomUUID();

        // Standard verified resolver mapping authenticated userId to merchantId
        UserMerchantMappingResolver mappingResolver = uId -> uId.equals(userId)
                ? Optional.of(merchantId)
                : Optional.empty();

        controller = new DemoWebhookController(
                eventStore,
                authInternalClient,
                objectMapper,
                mappingResolver,
                environment,
                null,
                null
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        samplePayload = "{\"eventType\":\"parcel.status.updated\",\"parcelId\":\"" + UUID.randomUUID()
                + "\",\"status\":\"IN_TRANSIT\"}";
        validSignature = HmacSignatureUtil.generateSignatureHeader(samplePayload, TEST_SECRET);
    }

    // ─── POST /demo-receiver Signature & Validation Tests ────────────────

    @Test
    @DisplayName("Should accept and store event when valid HMAC-SHA256 signature is provided")
    void shouldAcceptValidSignature() throws Exception {
        MerchantWebhookConfigResponse config = new MerchantWebhookConfigResponse(
                merchantId, "http://localhost:8083/api/v1/webhooks/demo-receiver", TEST_SECRET, true);
        when(authInternalClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.of(config));

        mockMvc.perform(post(DEMO_RECEIVER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Merchant-Id", merchantId.toString())
                        .header(HmacSignatureUtil.SIGNATURE_HEADER_NAME, validSignature)
                        .content(samplePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.eventId").isNotEmpty());

        List<DemoWebhookEvent> events = eventStore.getRecentEvents(merchantId);
        assertEquals(1, events.size());
        assertEquals("parcel.status.updated", events.get(0).eventType());
        assertTrue(events.get(0).verified());
        assertEquals(samplePayload, events.get(0).payload());
    }

    @Test
    @DisplayName("Should reject request with 400 Bad Request when signature header is missing")
    void shouldRejectMissingSignatureHeader() throws Exception {
        mockMvc.perform(post(DEMO_RECEIVER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Merchant-Id", merchantId.toString())
                        .content(samplePayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_SIGNATURE"));

        assertEquals(0, eventStore.getRecentEvents(merchantId).size());
    }

    @Test
    @DisplayName("Should reject request with 400 Bad Request when signature header prefix is missing")
    void shouldRejectMalformedSignaturePrefix() throws Exception {
        String malformedSig = "rawhexwithoutprefix123456";

        mockMvc.perform(post(DEMO_RECEIVER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Merchant-Id", merchantId.toString())
                        .header(HmacSignatureUtil.SIGNATURE_HEADER_NAME, malformedSig)
                        .content(samplePayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_SIGNATURE"));

        assertEquals(0, eventStore.getRecentEvents(merchantId).size());
    }

    @Test
    @DisplayName("Should reject request with 401 Unauthorized when signature does not match secret")
    void shouldRejectInvalidSignature() throws Exception {
        MerchantWebhookConfigResponse config = new MerchantWebhookConfigResponse(
                merchantId, "http://localhost:8083/demo", TEST_SECRET, true);
        when(authInternalClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.of(config));

        String invalidSignature = "sha256=0000000000000000000000000000000000000000000000000000000000000000";

        mockMvc.perform(post(DEMO_RECEIVER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Merchant-Id", merchantId.toString())
                        .header(HmacSignatureUtil.SIGNATURE_HEADER_NAME, invalidSignature)
                        .content(samplePayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_SIGNATURE"));

        assertEquals(0, eventStore.getRecentEvents(merchantId).size());
    }

    @Test
    @DisplayName("Should reject request with 401 Unauthorized when payload was tampered in transit")
    void shouldRejectTamperedPayload() throws Exception {
        MerchantWebhookConfigResponse config = new MerchantWebhookConfigResponse(
                merchantId, "http://localhost:8083/demo", TEST_SECRET, true);
        when(authInternalClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.of(config));

        String tamperedPayload = "{\"eventType\":\"parcel.status.updated\",\"status\":\"DELIVERED_TAMPERED\"}";

        mockMvc.perform(post(DEMO_RECEIVER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Merchant-Id", merchantId.toString())
                        .header(HmacSignatureUtil.SIGNATURE_HEADER_NAME, validSignature)
                        .content(tamperedPayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_SIGNATURE"));

        assertEquals(0, eventStore.getRecentEvents(merchantId).size());
    }

    @Test
    @DisplayName("Should reject request with 401 Unauthorized when merchant config is not found in Auth Service")
    void shouldRejectWhenMerchantConfigUnavailable() throws Exception {
        when(authInternalClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.empty());

        mockMvc.perform(post(DEMO_RECEIVER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Merchant-Id", merchantId.toString())
                        .header(HmacSignatureUtil.SIGNATURE_HEADER_NAME, validSignature)
                        .content(samplePayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("CONFIG_UNAVAILABLE"));

        assertEquals(0, eventStore.getRecentEvents(merchantId).size());
    }

    @Test
    @DisplayName("Should reject request with 400 Bad Request when candidate merchant ID cannot be resolved")
    void shouldRejectMissingMerchantId() throws Exception {
        mockMvc.perform(post(DEMO_RECEIVER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HmacSignatureUtil.SIGNATURE_HEADER_NAME, validSignature)
                        .content(samplePayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_MERCHANT_ID"));

        verifyNoInteractions(authInternalClient);
    }

    @Test
    @DisplayName("Should reject request with 400 Bad Request when payload is empty")
    void shouldRejectEmptyPayload() throws Exception {
        mockMvc.perform(post(DEMO_RECEIVER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Merchant-Id", merchantId.toString())
                        .header(HmacSignatureUtil.SIGNATURE_HEADER_NAME, validSignature)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    // ─── Fallback Demo Secret Hardening Tests ────────────────────────────

    @Test
    @DisplayName("Should accept fallback demo secret when merchant ID explicitly matches in non-prod")
    void shouldAcceptMatchingDemoSecretInNonProd() throws Exception {
        when(authInternalClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.empty());
        when(environment.matchesProfiles("prod", "production")).thenReturn(false);

        DemoWebhookController fallbackController = new DemoWebhookController(
                eventStore, authInternalClient, objectMapper,
                (uId -> Optional.of(merchantId)),
                environment,
                TEST_SECRET,
                merchantId.toString() // Explicit matching merchant ID
        );
        MockMvc fallbackMockMvc = MockMvcBuilders.standaloneSetup(fallbackController).build();

        fallbackMockMvc.perform(post(DEMO_RECEIVER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Merchant-Id", merchantId.toString())
                        .header(HmacSignatureUtil.SIGNATURE_HEADER_NAME, validSignature)
                        .content(samplePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));
    }

    @Test
    @DisplayName("Should reject fallback secret when merchant ID does not match configured demo merchant")
    void shouldRejectFallbackSecretWhenMerchantMismatch() throws Exception {
        UUID otherMerchantId = UUID.randomUUID();
        when(authInternalClient.getMerchantWebhookConfig(otherMerchantId)).thenReturn(Optional.empty());
        when(environment.matchesProfiles("prod", "production")).thenReturn(false);

        DemoWebhookController fallbackController = new DemoWebhookController(
                eventStore, authInternalClient, objectMapper,
                (uId -> Optional.of(merchantId)),
                environment,
                TEST_SECRET,
                merchantId.toString() // Configured for merchantId, NOT otherMerchantId
        );
        MockMvc fallbackMockMvc = MockMvcBuilders.standaloneSetup(fallbackController).build();

        fallbackMockMvc.perform(post(DEMO_RECEIVER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Merchant-Id", otherMerchantId.toString())
                        .header(HmacSignatureUtil.SIGNATURE_HEADER_NAME, validSignature)
                        .content(samplePayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("CONFIG_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Should reject fallback secret when fallback merchant ID is blank (no global secret allowed)")
    void shouldRejectFallbackSecretWhenMerchantIdIsBlank() throws Exception {
        when(authInternalClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.empty());
        when(environment.matchesProfiles("prod", "production")).thenReturn(false);

        DemoWebhookController fallbackController = new DemoWebhookController(
                eventStore, authInternalClient, objectMapper,
                (uId -> Optional.of(merchantId)),
                environment,
                TEST_SECRET,
                "" // Blank merchant ID — global secret forbidden!
        );
        MockMvc fallbackMockMvc = MockMvcBuilders.standaloneSetup(fallbackController).build();

        fallbackMockMvc.perform(post(DEMO_RECEIVER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Merchant-Id", merchantId.toString())
                        .header(HmacSignatureUtil.SIGNATURE_HEADER_NAME, validSignature)
                        .content(samplePayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("CONFIG_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Should disable fallback secret in production environment even if merchant matches")
    void shouldDisableFallbackSecretInProduction() throws Exception {
        when(authInternalClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.empty());
        when(environment.matchesProfiles("prod", "production")).thenReturn(true); // Production profile active!

        DemoWebhookController fallbackController = new DemoWebhookController(
                eventStore, authInternalClient, objectMapper,
                (uId -> Optional.of(merchantId)),
                environment,
                TEST_SECRET,
                merchantId.toString()
        );
        MockMvc fallbackMockMvc = MockMvcBuilders.standaloneSetup(fallbackController).build();

        fallbackMockMvc.perform(post(DEMO_RECEIVER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Merchant-Id", merchantId.toString())
                        .header(HmacSignatureUtil.SIGNATURE_HEADER_NAME, validSignature)
                        .content(samplePayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("CONFIG_UNAVAILABLE"));
    }

    // ─── GET & DELETE Security, Isolation & Fail-Closed Tests ───────────

    @Test
    @DisplayName("Should return recent demo events for authenticated merchant via verified mapping")
    void shouldReturnRecentEventsForAuthenticatedMerchant() throws Exception {
        DemoWebhookEvent event1 = DemoWebhookEvent.verified(merchantId, "parcel.created", "{\"order\":\"1\"}");
        DemoWebhookEvent event2 = DemoWebhookEvent.verified(merchantId, "parcel.status.updated", "{\"order\":\"2\"}");
        eventStore.addEvent(merchantId, event1);
        eventStore.addEvent(merchantId, event2);

        mockMvc.perform(get(DEMO_EVENTS_URL)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.events", hasSize(2)))
                .andExpect(jsonPath("$.events[0].eventType").value("parcel.status.updated"))
                .andExpect(jsonPath("$.events[1].eventType").value("parcel.created"));
    }

    @Test
    @DisplayName("Should reject GET /demo-events with 403 Forbidden when caller attempts to forge X-Merchant-Id")
    void shouldRejectForgedMerchantIdentityOnGet() throws Exception {
        UUID victimMerchantId = UUID.randomUUID();

        mockMvc.perform(get(DEMO_EVENTS_URL)
                        .header("X-User-Id", userId.toString()) // Authenticated user maps to merchantId
                        .header("X-User-Role", "ROLE_MERCHANT")
                        .header("X-Merchant-Id", victimMerchantId.toString())) // Attacker supplies victim's ID
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORGED_MERCHANT_IDENTITY"));
    }

    @Test
    @DisplayName("Should reject DELETE /demo-events with 403 Forbidden when caller attempts to forge X-Merchant-Id")
    void shouldRejectForgedMerchantIdentityOnDelete() throws Exception {
        UUID victimMerchantId = UUID.randomUUID();

        mockMvc.perform(delete(DEMO_EVENTS_URL)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT")
                        .header("X-Merchant-Id", victimMerchantId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORGED_MERCHANT_IDENTITY"));
    }

    @Test
    @DisplayName("Should fail closed with 503 Service Unavailable on GET when trusted mapping is unavailable")
    void shouldFailClosedOnGetWhenMappingUnavailable() throws Exception {
        DemoWebhookController failClosedController = new DemoWebhookController(
                eventStore, authInternalClient, objectMapper,
                (uId -> Optional.empty()), // No mapping available!
                environment, null, null
        );
        MockMvc failClosedMvc = MockMvcBuilders.standaloneSetup(failClosedController).build();

        failClosedMvc.perform(get(DEMO_EVENTS_URL)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("AUTH_MAPPING_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Should fail closed with 503 Service Unavailable on DELETE when trusted mapping is unavailable")
    void shouldFailClosedOnDeleteWhenMappingUnavailable() throws Exception {
        DemoWebhookController failClosedController = new DemoWebhookController(
                eventStore, authInternalClient, objectMapper,
                (uId -> Optional.empty()), // No mapping available!
                environment, null, null
        );
        MockMvc failClosedMvc = MockMvcBuilders.standaloneSetup(failClosedController).build();

        failClosedMvc.perform(delete(DEMO_EVENTS_URL)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("AUTH_MAPPING_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Should reject GET /demo-events with 401 Unauthorized when X-User-Id is missing")
    void shouldRejectUnauthenticatedGetEvents() throws Exception {
        mockMvc.perform(get(DEMO_EVENTS_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("Should reject DELETE /demo-events with 401 Unauthorized when X-User-Id is missing")
    void shouldRejectUnauthenticatedDeleteEvents() throws Exception {
        mockMvc.perform(delete(DEMO_EVENTS_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("Should reject GET /demo-events with 403 Forbidden when role is unauthorized (e.g. ROLE_CUSTOMER)")
    void shouldRejectUnauthorizedGetRole() throws Exception {
        mockMvc.perform(get(DEMO_EVENTS_URL)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "ROLE_CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Should reject DELETE /demo-events with 403 Forbidden when role is unauthorized (e.g. ROLE_CUSTOMER)")
    void shouldRejectUnauthorizedDeleteRole() throws Exception {
        mockMvc.perform(delete(DEMO_EVENTS_URL)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "ROLE_CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Should clear merchant demo events when DELETE /demo-events is called by authorized merchant")
    void shouldClearDemoEventsForMerchant() throws Exception {
        eventStore.addEvent(merchantId, DemoWebhookEvent.verified(merchantId, "parcel.created", "{}"));
        assertEquals(1, eventStore.getEventCount(merchantId));

        mockMvc.perform(delete(DEMO_EVENTS_URL)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Demo webhook events cleared successfully"))
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()));

        assertEquals(0, eventStore.getEventCount(merchantId));
    }

    // ─── DemoWebhookEventStore Direct Bounded Ring Buffer Tests ──────────

    @Test
    @DisplayName("Should enforce maximum bound of 100 events per merchant and evict oldest FIFO")
    void shouldEnforceMax100EventsPerMerchant() {
        for (int i = 1; i <= 105; i++) {
            DemoWebhookEvent event = DemoWebhookEvent.verified(
                    merchantId, "event." + i, "{\"seq\":" + i + "}");
            eventStore.addEvent(merchantId, event);
        }

        List<DemoWebhookEvent> stored = eventStore.getRecentEvents(merchantId);
        assertEquals(DemoWebhookEventStore.MAX_EVENTS_PER_MERCHANT, stored.size());
        assertEquals(100, stored.size());

        // Newest event should be event #105
        assertEquals("event.105", stored.get(0).eventType());
        // Oldest retained event should be event #6 (events 1-5 evicted)
        assertEquals("event.6", stored.get(99).eventType());
    }

    @Test
    @DisplayName("Should handle null or invalid inputs gracefully in event store")
    void shouldHandleNullInputsInEventStore() {
        assertDoesNotThrow(() -> eventStore.addEvent(null, null));
        assertDoesNotThrow(() -> eventStore.addEvent(merchantId, null));
        assertDoesNotThrow(() -> eventStore.addEvent(null, DemoWebhookEvent.verified(merchantId, "test", "{}")));

        assertTrue(eventStore.getRecentEvents(null).isEmpty());
        assertEquals(0, eventStore.getEventCount(null));
        assertDoesNotThrow(() -> eventStore.clearEvents(null));
    }
}
