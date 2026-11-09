package com.nyxora.swiftdrop.notification.webhook.controller;

import com.nyxora.swiftdrop.notification.webhook.demo.UserMerchantMappingResolver;
import com.nyxora.swiftdrop.notification.webhook.dto.ManualRetryResponse;
import com.nyxora.swiftdrop.notification.webhook.dto.WebhookLogsPageResponse;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;
import com.nyxora.swiftdrop.notification.webhook.exception.ConcurrentRetryException;
import com.nyxora.swiftdrop.notification.webhook.exception.DeliveryLogNotFoundException;
import com.nyxora.swiftdrop.notification.webhook.exception.InvalidDeliveryStatusException;
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
import java.util.Optional;
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
 *   <li>Requires authenticated caller identity via API Gateway verified {@code X-User-Id} and role {@code X-User-Role}.</li>
 *   <li>Enforces zero-trust: Rejects treating {@code users.id} as {@code merchants.id}.</li>
 *   <li>Resolves merchant UUID strictly through {@link UserMerchantMappingResolver}. Fails closed if unavailable.</li>
 *   <li>Rejects caller-supplied {@code X-Merchant-Id} if it contradicts the verified user session (anti-spoofing).</li>
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
    private final UserMerchantMappingResolver mappingResolver;

    @Autowired
    public WebhookLogController(
            WebhookLogService logService,
            @Autowired(required = false) UserMerchantMappingResolver mappingResolver) {
        this.logService = logService;
        this.mappingResolver = mappingResolver != null ? mappingResolver : (userId -> Optional.empty());
    }

    /**
     * Retrieves paginated delivery logs belonging to the authenticated merchant.
     *
     * @param page             zero-indexed page number (default 0)
     * @param size             items per page (default 20, max 100)
     * @param statusStr        optional status filter (PENDING, DELIVERED, RETRYING, DEAD_LETTER)
     * @param userIdHeader     {@code X-User-Id} injected by API Gateway
     * @param merchantIdHeader optional {@code X-Merchant-Id} header (anti-spoof checked)
     * @param userRoleHeader   {@code X-User-Role} injected by API Gateway
     * @return paginated delivery records
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getDeliveryLogs(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "status", required = false) String statusStr,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-Merchant-Id", required = false) String merchantIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRoleHeader) {

        // 1. Authenticate caller identity
        UUID userId = parseAuthenticatedUserId(userIdHeader);
        if (userId == null) {
            log.warn("Rejected unauthenticated request to GET /api/v1/webhooks/logs: missing X-User-Id");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "UNAUTHENTICATED", "message", "Missing trusted caller identity header (X-User-Id)"));
        }

        // 2. Enforce Role-Based Access Control
        if (!isAuthorizedRole(userRoleHeader)) {
            log.warn("Rejected unauthorized role [{}] access to GET /api/v1/webhooks/logs for userId [{}]",
                    userRoleHeader, userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "FORBIDDEN", "message", "Access restricted to merchants and administrators"));
        }

        // 3. Resolve verified merchant ID via trusted mapping (Fail-Closed)
        Optional<UUID> verifiedMerchantOpt = mappingResolver.resolveMerchantId(userId);
        if (verifiedMerchantOpt.isEmpty()) {
            log.warn("GET /api/v1/webhooks/logs failed closed: no trusted user-to-merchant mapping for userId={}", userId);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "AUTH_MAPPING_UNAVAILABLE",
                            "message", "Trusted user-to-merchant mapping is unavailable. "
                                    + "User IDs and Merchant IDs are separate identifiers. "
                                    + "Auth Service mapping (Task 2.1) or Gateway claim enrichment (Task 4.3) is required. "
                                    + "Failing closed to prevent unauthorized merchant impersonation."));
        }

        UUID verifiedMerchantId = verifiedMerchantOpt.get();

        // 4. Anti-Spoofing Check: Caller-supplied X-Merchant-Id must not contradict verified mapping
        if (isForgedMerchantIdentity(merchantIdHeader, verifiedMerchantId)) {
            log.warn("Rejected forged merchant identity on GET /api/v1/webhooks/logs: caller supplied {}, user maps to {}",
                    merchantIdHeader, verifiedMerchantId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "FORGED_MERCHANT_IDENTITY",
                            "message", "Caller-supplied X-Merchant-Id does not match verified merchant identity for authenticated user"));
        }

        // 5. Validate pagination parameters
        if (page < 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "INVALID_PAGINATION", "message", "Page index must not be negative"));
        }
        if (size < 1) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "INVALID_PAGINATION", "message", "Page size must be greater than zero"));
        }
        int effectiveSize = Math.min(size, MAX_PAGE_SIZE);

        // 6. Validate optional status filter
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

        // 7. Execute paginated query (newest deliveries first by default)
        Pageable pageable = PageRequest.of(page, effectiveSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        WebhookLogsPageResponse response = logService.getMerchantLogs(verifiedMerchantId, statusFilter, pageable);

        return ResponseEntity.ok(response);
    }

    /**
     * Allows an authenticated merchant to manually retry an eligible failed webhook delivery.
     *
     * @param deliveryLogId    UUID of the delivery log to retry
     * @param userIdHeader     {@code X-User-Id} injected by API Gateway
     * @param merchantIdHeader optional {@code X-Merchant-Id} header (anti-spoof checked)
     * @param userRoleHeader   {@code X-User-Role} injected by API Gateway
     * @return acceptance confirmation
     */
    @PostMapping(value = "/{id}/retry", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> retryDelivery(
            @PathVariable("id") UUID deliveryLogId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-Merchant-Id", required = false) String merchantIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRoleHeader) {

        // 1. Authenticate caller identity
        UUID userId = parseAuthenticatedUserId(userIdHeader);
        if (userId == null) {
            log.warn("Rejected unauthenticated request to POST /api/v1/webhooks/logs/{}/retry: missing X-User-Id", deliveryLogId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "UNAUTHENTICATED", "message", "Missing trusted caller identity header (X-User-Id)"));
        }

        // 2. Enforce Role-Based Access Control
        if (!isAuthorizedRole(userRoleHeader)) {
            log.warn("Rejected unauthorized role [{}] access to POST /api/v1/webhooks/logs/{}/retry for userId [{}]",
                    userRoleHeader, deliveryLogId, userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "FORBIDDEN", "message", "Access restricted to merchants and administrators"));
        }

        // 3. Resolve verified merchant ID via trusted mapping (Fail-Closed)
        Optional<UUID> verifiedMerchantOpt = mappingResolver.resolveMerchantId(userId);
        if (verifiedMerchantOpt.isEmpty()) {
            log.warn("POST /api/v1/webhooks/logs/{}/retry failed closed: no trusted user-to-merchant mapping for userId={}",
                    deliveryLogId, userId);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "AUTH_MAPPING_UNAVAILABLE",
                            "message", "Trusted user-to-merchant mapping is unavailable. "
                                    + "User IDs and Merchant IDs are separate identifiers. "
                                    + "Failing closed to prevent unauthorized merchant impersonation."));
        }

        UUID verifiedMerchantId = verifiedMerchantOpt.get();

        // 4. Anti-Spoofing Check: Caller-supplied X-Merchant-Id must not contradict verified mapping
        if (isForgedMerchantIdentity(merchantIdHeader, verifiedMerchantId)) {
            log.warn("Rejected forged merchant identity on retry: caller supplied {}, user maps to {}",
                    merchantIdHeader, verifiedMerchantId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "FORGED_MERCHANT_IDENTITY",
                            "message", "Caller-supplied X-Merchant-Id does not match verified merchant identity for authenticated user"));
        }

        // 5. Execute manual retry via service
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
            log.error("Internal error executing manual retry for deliveryLogId={}: {}", deliveryLogId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "RETRY_PUBLICATION_FAILED", "message", ex.getMessage()));
        }
    }

    // ─── Private Helper Methods ──────────────────────────────────────────

    private UUID parseAuthenticatedUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(userIdHeader.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean isAuthorizedRole(String userRoleHeader) {
        if (userRoleHeader == null || userRoleHeader.isBlank()) {
            return false;
        }
        return userRoleHeader.contains("MERCHANT") || userRoleHeader.contains("ADMIN");
    }

    private boolean isForgedMerchantIdentity(String candidateHeader, UUID verifiedMerchantId) {
        if (candidateHeader == null || candidateHeader.isBlank()) {
            return false; // Header omitted, rely strictly on verified mapping
        }
        try {
            UUID supplied = UUID.fromString(candidateHeader.trim());
            return !supplied.equals(verifiedMerchantId);
        } catch (IllegalArgumentException ex) {
            return true; // Malformed header is rejected as untrusted
        }
    }
}

