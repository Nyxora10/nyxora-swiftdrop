package com.nyxora.swiftdrop.notification.webhook.controller;

import com.nyxora.swiftdrop.notification.webhook.dto.ManualRetryResponse;
import com.nyxora.swiftdrop.notification.webhook.dto.WebhookDeliveryLogResponse;
import com.nyxora.swiftdrop.notification.webhook.dto.WebhookLogsPageResponse;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;
import com.nyxora.swiftdrop.notification.webhook.exception.ConcurrentRetryException;
import com.nyxora.swiftdrop.notification.webhook.exception.DeliveryLogNotFoundException;
import com.nyxora.swiftdrop.notification.webhook.exception.InvalidDeliveryStatusException;
import com.nyxora.swiftdrop.notification.webhook.security.GatewaySecurityValidator;
import com.nyxora.swiftdrop.notification.webhook.service.WebhookLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookLogController Web Layer Security & API Unit Tests")
class WebhookLogControllerTest {

    private static final String BASE_URL = "/api/v1/webhooks/logs";
    private static final String TEST_GATEWAY_SECRET = "test-gateway-shared-secret-12345";

    @Mock
    private WebhookLogService logService;

    private WebhookLogController controller;
    private MockMvc mockMvc;

    private UUID userId;
    private UUID merchantId;
    private UUID logId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        merchantId = UUID.randomUUID();
        logId = UUID.randomUUID();

        GatewaySecurityValidator securityValidator = new GatewaySecurityValidator(null, TEST_GATEWAY_SECRET);
        controller = new WebhookLogController(logService, securityValidator);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ─── GET /api/v1/webhooks/logs Tests ─────────────────────────────────

    @Test
    @DisplayName("Should return paginated delivery logs for authenticated merchant via verified Gateway identity")
    void shouldReturnPaginatedLogsForMerchant() throws Exception {
        WebhookDeliveryLogResponse item = new WebhookDeliveryLogResponse(
                logId, UUID.randomUUID(), merchantId, "parcel.status.updated",
                200, 0, WebhookDeliveryStatus.DELIVERED, null, Instant.now());
        WebhookLogsPageResponse pageResponse = new WebhookLogsPageResponse(
                List.of(item), 0, 20, 1, 1, true, true);

        when(logService.getMerchantLogs(eq(merchantId), isNull(), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get(BASE_URL)
                        .header("X-Gateway-Secret", TEST_GATEWAY_SECRET)
                        .header("X-User-Id", userId.toString())
                        .header("X-Merchant-Id", merchantId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(logId.toString()))
                .andExpect(jsonPath("$.content[0].merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Should support status filtering on GET /logs")
    void shouldFilterLogsByStatus() throws Exception {
        WebhookDeliveryLogResponse item = new WebhookDeliveryLogResponse(
                logId, UUID.randomUUID(), merchantId, "parcel.status.updated",
                500, 5, WebhookDeliveryStatus.DEAD_LETTER, null, Instant.now());
        WebhookLogsPageResponse pageResponse = new WebhookLogsPageResponse(
                List.of(item), 0, 20, 1, 1, true, true);

        when(logService.getMerchantLogs(eq(merchantId), eq(WebhookDeliveryStatus.DEAD_LETTER), any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get(BASE_URL)
                        .header("X-Gateway-Secret", TEST_GATEWAY_SECRET)
                        .header("X-User-Id", userId.toString())
                        .header("X-Merchant-Id", merchantId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT")
                        .param("status", "DEAD_LETTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].deliveryStatus").value("DEAD_LETTER"));
    }

    @Test
    @DisplayName("Should reject GET /logs with 401 when X-Gateway-Secret is missing")
    void shouldRejectMissingGatewaySecretOnGetLogs() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("X-User-Id", userId.toString())
                        .header("X-Merchant-Id", merchantId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_GATEWAY_SECRET"));
    }

    @Test
    @DisplayName("Should reject GET /logs with 401 when X-Gateway-Secret is incorrect")
    void shouldRejectIncorrectGatewaySecretOnGetLogs() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("X-Gateway-Secret", "wrong-secret")
                        .header("X-User-Id", userId.toString())
                        .header("X-Merchant-Id", merchantId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_GATEWAY_SECRET"));
    }

    @Test
    @DisplayName("Should reject GET /logs with 400 when X-Merchant-Id is missing")
    void shouldRejectMissingMerchantIdOnGetLogs() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("X-Gateway-Secret", TEST_GATEWAY_SECRET)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_MERCHANT_IDENTITY"));
    }

    @Test
    @DisplayName("Should reject GET /logs with 400 Bad Request when page number is negative")
    void shouldRejectNegativePageNumber() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("X-Gateway-Secret", TEST_GATEWAY_SECRET)
                        .header("X-User-Id", userId.toString())
                        .header("X-Merchant-Id", merchantId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PAGINATION"));
    }

    @Test
    @DisplayName("Should reject GET /logs with 400 Bad Request when page size is zero or negative")
    void shouldRejectInvalidPageSize() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("X-Gateway-Secret", TEST_GATEWAY_SECRET)
                        .header("X-User-Id", userId.toString())
                        .header("X-Merchant-Id", merchantId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PAGINATION"));
    }

    @Test
    @DisplayName("Should reject GET /logs with 400 Bad Request when status filter is invalid")
    void shouldRejectInvalidStatusFilter() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("X-Gateway-Secret", TEST_GATEWAY_SECRET)
                        .header("X-User-Id", userId.toString())
                        .header("X-Merchant-Id", merchantId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT")
                        .param("status", "UNKNOWN_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_STATUS"));
    }

    @Test
    @DisplayName("Should reject GET /logs with 401 Unauthorized when X-User-Id is missing")
    void shouldRejectUnauthenticatedGetLogs() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("X-Gateway-Secret", TEST_GATEWAY_SECRET)
                        .header("X-Merchant-Id", merchantId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("Should reject GET /logs with 403 Forbidden when role is unauthorized (e.g. ROLE_CUSTOMER)")
    void shouldRejectUnauthorizedRoleOnGetLogs() throws Exception {
        mockMvc.perform(get(BASE_URL)
                        .header("X-Gateway-Secret", TEST_GATEWAY_SECRET)
                        .header("X-User-Id", userId.toString())
                        .header("X-Merchant-Id", merchantId.toString())
                        .header("X-User-Role", "ROLE_CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Should reject GET /logs with 403 Forbidden when caller supplies conflicting candidate merchantId")
    void shouldRejectForgedMerchantIdOnGetLogs() throws Exception {
        UUID forgedMerchantId = UUID.randomUUID();

        mockMvc.perform(get(BASE_URL)
                        .header("X-Gateway-Secret", TEST_GATEWAY_SECRET)
                        .header("X-User-Id", userId.toString())
                        .header("X-Merchant-Id", merchantId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT")
                        .param("merchantId", forgedMerchantId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORGED_MERCHANT_IDENTITY"));
    }

    // ─── POST /api/v1/webhooks/logs/{id}/retry Tests ─────────────────────

    @Test
    @DisplayName("Should accept manual retry for eligible DEAD_LETTER delivery")
    void shouldAcceptManualRetry() throws Exception {
        ManualRetryResponse response = ManualRetryResponse.accepted(
                logId, merchantId, WebhookDeliveryStatus.DEAD_LETTER, WebhookDeliveryStatus.RETRYING,
                "Manual webhook retry request accepted and enqueued for dispatch");

        when(logService.retryFailedDelivery(logId, merchantId)).thenReturn(response);

        mockMvc.perform(post(BASE_URL + "/{id}/retry", logId)
                        .header("X-Gateway-Secret", TEST_GATEWAY_SECRET)
                        .header("X-User-Id", userId.toString())
                        .header("X-Merchant-Id", merchantId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.deliveryLogId").value(logId.toString()))
                .andExpect(jsonPath("$.previousStatus").value("DEAD_LETTER"))
                .andExpect(jsonPath("$.newStatus").value("RETRYING"));
    }

    @Test
    @DisplayName("Should return 404 Not Found when delivery log does not exist or belongs to another merchant")
    void shouldReturn404WhenLogNotFoundOrCrossMerchant() throws Exception {
        when(logService.retryFailedDelivery(logId, merchantId))
                .thenThrow(new DeliveryLogNotFoundException("Webhook delivery log not found: " + logId));

        mockMvc.perform(post(BASE_URL + "/{id}/retry", logId)
                        .header("X-Gateway-Secret", TEST_GATEWAY_SECRET)
                        .header("X-User-Id", userId.toString())
                        .header("X-Merchant-Id", merchantId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Should return 409 Conflict when delivery log is in an ineligible status (e.g. DELIVERED)")
    void shouldReturn409WhenStatusIneligible() throws Exception {
        when(logService.retryFailedDelivery(logId, merchantId))
                .thenThrow(new InvalidDeliveryStatusException(
                        "Only deliveries in DEAD_LETTER status are eligible for manual retry",
                        WebhookDeliveryStatus.DELIVERED));

        mockMvc.perform(post(BASE_URL + "/{id}/retry", logId)
                        .header("X-Gateway-Secret", TEST_GATEWAY_SECRET)
                        .header("X-User-Id", userId.toString())
                        .header("X-Merchant-Id", merchantId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INELIGIBLE_STATUS"))
                .andExpect(jsonPath("$.currentStatus").value("DELIVERED"));
    }

    @Test
    @DisplayName("Should return 409 Conflict when concurrent retry request is detected")
    void shouldReturn409OnConcurrentRetryConflict() throws Exception {
        when(logService.retryFailedDelivery(logId, merchantId))
                .thenThrow(new ConcurrentRetryException("A concurrent retry request was already processed"));

        mockMvc.perform(post(BASE_URL + "/{id}/retry", logId)
                        .header("X-Gateway-Secret", TEST_GATEWAY_SECRET)
                        .header("X-User-Id", userId.toString())
                        .header("X-Merchant-Id", merchantId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONCURRENT_RETRY_CONFLICT"));
    }

    @Test
    @DisplayName("Should return 500 Internal Server Error when RabbitMQ publish fails")
    void shouldReturn500WhenPublishFails() throws Exception {
        when(logService.retryFailedDelivery(logId, merchantId))
                .thenThrow(new IllegalStateException("RabbitMQ publication failed during manual retry"));

        mockMvc.perform(post(BASE_URL + "/{id}/retry", logId)
                        .header("X-Gateway-Secret", TEST_GATEWAY_SECRET)
                        .header("X-User-Id", userId.toString())
                        .header("X-Merchant-Id", merchantId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("RETRY_PUBLICATION_FAILED"));
    }

    @Test
    @DisplayName("Should reject retry with 401 Unauthorized when X-Gateway-Secret is missing")
    void shouldRejectMissingGatewaySecretOnRetry() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{id}/retry", logId)
                        .header("X-User-Id", userId.toString())
                        .header("X-Merchant-Id", merchantId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_GATEWAY_SECRET"));
    }

    @Test
    @DisplayName("Should reject retry with 400 Bad Request when X-Merchant-Id is missing")
    void shouldRejectMissingMerchantIdOnRetry() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{id}/retry", logId)
                        .header("X-Gateway-Secret", TEST_GATEWAY_SECRET)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_MERCHANT_IDENTITY"));
    }

    @Test
    @DisplayName("Should reject retry with 401 Unauthorized when X-User-Id is missing")
    void shouldRejectUnauthenticatedRetry() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{id}/retry", logId)
                        .header("X-Gateway-Secret", TEST_GATEWAY_SECRET)
                        .header("X-Merchant-Id", merchantId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("Should reject retry with 403 Forbidden when role is unauthorized (e.g. ROLE_CUSTOMER)")
    void shouldRejectUnauthorizedRoleOnRetry() throws Exception {
        mockMvc.perform(post(BASE_URL + "/{id}/retry", logId)
                        .header("X-Gateway-Secret", TEST_GATEWAY_SECRET)
                        .header("X-User-Id", userId.toString())
                        .header("X-Merchant-Id", merchantId.toString())
                        .header("X-User-Role", "ROLE_CUSTOMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Should reject retry with 403 Forbidden when caller supplies conflicting candidate merchantId")
    void shouldRejectForgedMerchantIdOnRetry() throws Exception {
        UUID forgedMerchantId = UUID.randomUUID();

        mockMvc.perform(post(BASE_URL + "/{id}/retry", logId)
                        .header("X-Gateway-Secret", TEST_GATEWAY_SECRET)
                        .header("X-User-Id", userId.toString())
                        .header("X-Merchant-Id", merchantId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT")
                        .param("merchantId", forgedMerchantId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORGED_MERCHANT_IDENTITY"));
    }
}
