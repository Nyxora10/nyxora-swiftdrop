package com.nyxora.swiftdrop.notification.webhook.controller;

import com.nyxora.swiftdrop.common.security.util.PiiMaskingUtil;
import com.nyxora.swiftdrop.notification.webhook.demo.UserMerchantMappingResolver;
import com.nyxora.swiftdrop.notification.webhook.dto.ManualRetryResponse;
import com.nyxora.swiftdrop.notification.webhook.dto.WebhookLogsPageResponse;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;
import com.nyxora.swiftdrop.notification.webhook.exception.ConcurrentRetryException;
import com.nyxora.swiftdrop.notification.webhook.exception.DeliveryLogNotFoundException;
import com.nyxora.swiftdrop.notification.webhook.exception.InvalidDeliveryStatusException;
import com.nyxora.swiftdrop.notification.webhook.security.GatewaySecurityValidator;
import com.nyxora.swiftdrop.notification.webhook.service.WebhookLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST Controller providing Paginated Webhook Delivery Logs and Manual Retry API (Task 3.6).
 *
 * <p><strong>Endpoints:</strong>
 * <ul>
 *   <li>{@code GET /api/v1/webhooks/logs}: Returns paginated, status-filtered delivery logs for the authenticated merchant.</li>
 *   <li>{@code POST /api/v1/webhooks/logs/{id}/retry}: Triggers a manual retry for an eligible DEAD_LETTER webhook delivery.</li>
 * </ul>
 *
 * <p><strong>Security Architecture:</strong>
 * <ul>
 *   <li>Validates trusted API Gateway shared secret via {@code X-Gateway-Secret}.</li>
 *   <li>Requires authenticated caller identity via API Gateway verified {@code X-User-Id} and role {@code X-User-Role}.</li>
 *   <li>Enforces zero-trust: Rejects treating {@code users.id} as {@code merchants.id}.</li>
 *   <li>Uses verified {@code X-Merchant-Id} supplied exclusively by the authenticated Gateway.</li>
 *   <li>Rejects caller-supplied candidate merchant IDs if they contradict the verified identity (anti-spoofing).</li>
 *   <li>Never accepts caller-supplied webhook URLs, secrets, or replacement payloads.</li>
 *   <li>Returns 404 if a log does not exist or belongs to another merchant (preventing ID enumeration).</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/webhooks/logs")
public class WebhookLogController {

    private static final Logger log = LoggerFactory.getLogger(WebhookLogController.class);

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private final WebhookLogService logService;
    private final GatewaySecurityValidator securityValidator;

    @Autowired
    public WebhookLogController(
            WebhookLogService logService,
            GatewaySecurityValidator securityValidator) {
        this.logService = logService;
        this.securityValidator = securityValidator;
    }

    /**
     * Deprecated constructor for backward-compatibility with tests using legacy mapping resolver.
     */
    @Deprecated
    public WebhookLogController(
            WebhookLogService logService,
            UserMerchantMappingResolver mappingResolver) {
        this(logService, new GatewaySecurityValidator(null, GatewaySecurityValidator.DEFAULT_DEV_SECRET));
    }

    /**
     * Retrieves paginated delivery logs belonging to the authenticated merchant.
     *
     * @param page                   zero-indexed page number (default 0)
     * @param size                   items per page (default 20, max 100)
     * @param statusStr              optional status filter (PENDING, DELIVERED, RETRYING, DEAD_LETTER)
     * @param gatewaySecretHeader    {@code X-Gateway-Secret} injected by API Gateway
     * @param userIdHeader           {@code X-User-Id} injected by API Gateway
     * @param merchantIdHeader       {@code X-Merchant-Id} injected by API Gateway
     * @param userRoleHeader         {@code X-User-Role} injected by API Gateway
     * @param candidateMerchantQuery optional candidate {@code merchantId} query parameter (anti-spoof checked)
     * @return paginated delivery records
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getDeliveryLogs(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "status", required = false) String statusStr,
            @RequestHeader(value = GatewaySecurityValidator.HEADER_GATEWAY_SECRET, required = false) String gatewaySecretHeader,
            @RequestHeader(value = GatewaySecurityValidator.HEADER_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = GatewaySecurityValidator.HEADER_MERCHANT_ID, required = false) String merchantIdHeader,
            @RequestHeader(value = GatewaySecurityValidator.HEADER_USER_ROLE, required = false) String userRoleHeader,
            @RequestParam(value = "merchantId", required = false) String candidateMerchantQuery) {

        // 1. Authenticate Gateway origin and merchant caller identity
        var authResult = securityValidator.authenticateMerchantRequest(
                gatewaySecretHeader, userIdHeader, userRoleHeader, merchantIdHeader, candidateMerchantQuery);

        if (authResult instanceof GatewaySecurityValidator.MerchantAuthResult.Failure fail) {
            return ResponseEntity.status(fail.status())
                    .body(Map.of("error", fail.error(), "message", fail.message()));
        }

        UUID verifiedMerchantId = ((GatewaySecurityValidator.MerchantAuthResult.Success) authResult).merchantId();

        // 2. Validate pagination parameters
        if (page < 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "INVALID_PAGINATION", "message", "Page index must not be negative"));
        }
        if (size < 1) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "INVALID_PAGINATION", "message", "Page size must be greater than zero"));
        }
        int effectiveSize = Math.min(size, MAX_PAGE_SIZE);

        // 3. Validate optional status filter
        WebhookDeliveryStatus statusFilter = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                statusFilter = WebhookDeliveryStatus.valueOf(statusStr.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "INVALID_STATUS",
                                "message", "Invalid delivery status filter: " + statusStr
                                        + ". Permitted values: PENDING, DELIVERED, RETRYING, DEAD_LETTER"));
            }
        }

        // 4. Execute paginated query (newest deliveries first by default)
        Pageable pageable = PageRequest.of(page, effectiveSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        WebhookLogsPageResponse response = logService.getMerchantLogs(verifiedMerchantId, statusFilter, pageable);

        return ResponseEntity.ok(response);
    }

    /**
     * Allows an authenticated merchant to manually retry an eligible failed webhook delivery.
     *
     * @param deliveryLogId          UUID of the delivery log to retry
     * @param gatewaySecretHeader    {@code X-Gateway-Secret} injected by API Gateway
     * @param userIdHeader           {@code X-User-Id} injected by API Gateway
     * @param merchantIdHeader       {@code X-Merchant-Id} injected by API Gateway
     * @param userRoleHeader         {@code X-User-Role} injected by API Gateway
     * @param candidateMerchantQuery optional candidate {@code merchantId} query parameter (anti-spoof checked)
     * @return acceptance confirmation
     */
    @PostMapping(value = "/{id}/retry", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> retryDelivery(
            @PathVariable("id") UUID deliveryLogId,
            @RequestHeader(value = GatewaySecurityValidator.HEADER_GATEWAY_SECRET, required = false) String gatewaySecretHeader,
            @RequestHeader(value = GatewaySecurityValidator.HEADER_USER_ID, required = false) String userIdHeader,
            @RequestHeader(value = GatewaySecurityValidator.HEADER_MERCHANT_ID, required = false) String merchantIdHeader,
            @RequestHeader(value = GatewaySecurityValidator.HEADER_USER_ROLE, required = false) String userRoleHeader,
            @RequestParam(value = "merchantId", required = false) String candidateMerchantQuery) {

        // 1. Authenticate Gateway origin and merchant caller identity
        var authResult = securityValidator.authenticateMerchantRequest(
                gatewaySecretHeader, userIdHeader, userRoleHeader, merchantIdHeader, candidateMerchantQuery);

        if (authResult instanceof GatewaySecurityValidator.MerchantAuthResult.Failure fail) {
            return ResponseEntity.status(fail.status())
                    .body(Map.of("error", fail.error(), "message", fail.message()));
        }

        UUID verifiedMerchantId = ((GatewaySecurityValidator.MerchantAuthResult.Success) authResult).merchantId();

        // 2. Execute manual retry via service
        try {
            ManualRetryResponse response = logService.retryFailedDelivery(deliveryLogId, verifiedMerchantId);
            return ResponseEntity.ok(response);

        } catch (DeliveryLogNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "NOT_FOUND", "message", ex.getMessage()));

        } catch (InvalidDeliveryStatusException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "INELIGIBLE_STATUS",
                            "currentStatus", ex.getCurrentStatus(),
                            "message", ex.getMessage()));

        } catch (ConcurrentRetryException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "CONCURRENT_RETRY_CONFLICT", "message", ex.getMessage()));

        } catch (IllegalStateException ex) {
            log.error("Internal error executing manual retry for deliveryLogId={}: {}",
                    deliveryLogId, PiiMaskingUtil.maskText(ex.getMessage()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "RETRY_PUBLICATION_FAILED", "message", ex.getMessage()));
        }
    }
}
