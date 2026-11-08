package com.nyxora.swiftdrop.notification.webhook.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.notification.webhook.client.AuthInternalClient;
import com.nyxora.swiftdrop.notification.webhook.client.dto.MerchantWebhookConfigResponse;
import com.nyxora.swiftdrop.notification.webhook.demo.dto.DemoWebhookEvent;
import com.nyxora.swiftdrop.notification.webhook.demo.dto.DemoWebhookEventsResponse;
import com.nyxora.swiftdrop.notification.webhook.demo.dto.DemoWebhookReceiverResponse;
import com.nyxora.swiftdrop.notification.webhook.util.HmacSignatureUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST Controller implementing the Built-in Demo Webhook Receiver and Dashboard Polling API (Task 3.5).
 *
 * <p><strong>Purpose:</strong>
 * Provides a reliable, local live target for outbound merchant webhooks during examiner demonstrations
 * without requiring third-party tunneling or mock servers (e.g. ngrok, webhook.site).
 *
 * <p><strong>Endpoints:</strong>
 * <ul>
 *   <li>{@code POST /api/v1/webhooks/demo-receiver}: Accepts signed webhook dispatches, cryptographically
 *       verifies the {@code X-SwiftDrop-Signature} header using HMAC-SHA256, and stores verified payloads.</li>
 *   <li>{@code GET /api/v1/webhooks/demo-events}: Returns recent verified events for the authenticated merchant
 *       using verified user-to-merchant mapping.</li>
 *   <li>{@code DELETE /api/v1/webhooks/demo-events}: Clears the in-memory demo history for the authenticated merchant.</li>
 * </ul>
 *
 * <p><strong>Security Architecture & Hardening:</strong>
 * <ul>
 *   <li>Accepts exact raw payload bytes to prevent JSON re-serialization discrepancy during HMAC verification.</li>
 *   <li>Never trusts arbitrary merchant IDs or secrets supplied in request bodies.</li>
 *   <li>Resolves signing secrets exclusively from trusted server-side sources (Auth Service or explicit demo config).</li>
 *   <li>Development demo-secret fallback is disabled in production environments and requires an exact matching merchant ID.</li>
 *   <li>Never logs raw secrets, private keys, or full signature values.</li>
 *   <li>Rejects treating {@code X-User-Id} as {@code merchantId} because users and merchants have separate database identifiers.</li>
 *   <li>Fails closed on GET/DELETE if trusted user-to-merchant mapping is unavailable from Auth Service.</li>
 *   <li>Enforces strict RBAC (ROLE_MERCHANT / ROLE_ADMIN) on both GET and DELETE endpoints.</li>
 *   <li>Rejects caller-supplied {@code X-Merchant-Id} if it contradicts the verified user session (anti-spoofing).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class DemoWebhookController {

    private static final Logger log = LoggerFactory.getLogger(DemoWebhookController.class);

    private final DemoWebhookEventStore eventStore;
    private final AuthInternalClient authInternalClient;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final UserMerchantMappingResolver mappingResolver;
    private final String fallbackDemoSecret;
    private final String fallbackDemoMerchantId;

    @Autowired
    public DemoWebhookController(
            DemoWebhookEventStore eventStore,
            AuthInternalClient authInternalClient,
            @Autowired(required = false) ObjectMapper objectMapper,
            @Autowired(required = false) Environment environment,
            @Autowired(required = false) UserMerchantMappingResolver mappingResolver,
            @Value("${app.webhook.demo.secret:}") String fallbackDemoSecret,
            @Value("${app.webhook.demo.merchant-id:}") String fallbackDemoMerchantId) {
        this.eventStore = eventStore;
        this.authInternalClient = authInternalClient;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.environment = environment;
        this.mappingResolver = mappingResolver != null ? mappingResolver : (userId -> Optional.empty());
        this.fallbackDemoSecret = fallbackDemoSecret != null ? fallbackDemoSecret.trim() : "";
        this.fallbackDemoMerchantId = fallbackDemoMerchantId != null ? fallbackDemoMerchantId.trim() : "";
    }

    /**
     * Testing constructor with custom resolver and environment.
     */
    public DemoWebhookController(
            DemoWebhookEventStore eventStore,
            AuthInternalClient authInternalClient,
            ObjectMapper objectMapper,
            UserMerchantMappingResolver mappingResolver,
            Environment environment,
            String fallbackDemoSecret,
            String fallbackDemoMerchantId) {
        this(eventStore, authInternalClient, objectMapper, environment, mappingResolver, fallbackDemoSecret, fallbackDemoMerchantId);
    }

    /**
     * Minimal testing constructor.
     */
    public DemoWebhookController(
            DemoWebhookEventStore eventStore,
            AuthInternalClient authInternalClient,
            ObjectMapper objectMapper) {
        this(eventStore, authInternalClient, objectMapper, (Environment) null, (UserMerchantMappingResolver) null, null, null);
    }

    /**
     * Inbound receiver endpoint for signed webhook events.
     *
     * @param rawPayload              the exact bytes transmitted in the HTTP request body
     * @param signatureHeader         the {@code X-SwiftDrop-Signature} header
     * @param merchantIdHeader        optional {@code X-Merchant-Id} header identifying the target merchant
     * @param swiftDropMerchantHeader optional {@code X-SwiftDrop-Merchant-Id} header
     * @param merchantIdParam         optional {@code merchantId} query parameter
     * @return 200 OK on successful verification and storage; 400 or 401 on validation/signature failure
     */
    @PostMapping(value = "/demo-receiver", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> receiveDemoWebhook(
            @RequestBody(required = false) byte[] rawPayload,
            @RequestHeader(value = HmacSignatureUtil.SIGNATURE_HEADER_NAME, required = false) String signatureHeader,
            @RequestHeader(value = "X-Merchant-Id", required = false) String merchantIdHeader,
            @RequestHeader(value = "X-SwiftDrop-Merchant-Id", required = false) String swiftDropMerchantHeader,
            @RequestParam(value = "merchantId", required = false) String merchantIdParam) {

        // 1. Validate Payload Presence
        if (rawPayload == null || rawPayload.length == 0) {
            log.warn("Demo webhook receiver rejected request: empty payload body");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "BAD_REQUEST", "message", "Request body payload must not be empty"));
        }

        // 2. Validate Signature Header Presence and Format
        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.warn("Demo webhook receiver rejected request: missing signature header {}", HmacSignatureUtil.SIGNATURE_HEADER_NAME);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "MISSING_SIGNATURE",
                            "message", "Missing required header: " + HmacSignatureUtil.SIGNATURE_HEADER_NAME));
        }

        if (!signatureHeader.trim().startsWith(HmacSignatureUtil.SIGNATURE_PREFIX)) {
            log.warn("Demo webhook receiver rejected request: malformed signature prefix");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "MALFORMED_SIGNATURE",
                            "message", "Malformed signature header: missing 'sha256=' prefix"));
        }

        // 3. Resolve Candidate Target Merchant UUID
        UUID merchantId = resolveCandidateMerchantId(merchantIdHeader, swiftDropMerchantHeader, merchantIdParam);
        if (merchantId == null) {
            log.warn("Demo webhook receiver rejected request: unable to resolve candidate merchant identity");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "MISSING_MERCHANT_ID",
                            "message", "Merchant identification required via 'X-Merchant-Id' header or 'merchantId' query parameter"));
        }

        // 4. Resolve Trusted Signing Secret from Server-Side Source
        String signingSecret = resolveSigningSecret(merchantId);
        if (signingSecret == null || signingSecret.isBlank()) {
            log.warn("Demo webhook receiver: no active signing secret found for merchantId={}", merchantId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "CONFIG_UNAVAILABLE",
                            "message", "Merchant webhook configuration or signing secret could not be resolved from Auth Service"));
        }

        // 5. Cryptographically Verify Signature Against Exact Payload Bytes
        boolean isValid = HmacSignatureUtil.verifySignature(rawPayload, signingSecret, signatureHeader);
        if (!isValid) {
            log.warn("Demo webhook receiver: HMAC-SHA256 signature verification failed for merchantId={}", merchantId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "INVALID_SIGNATURE",
                            "message", "HMAC-SHA256 signature verification failed: signature does not match payload digest"));
        }

        // 6. Signature Verified — Extract Metadata and Persist into In-Memory Store
        String payloadJson = new String(rawPayload, StandardCharsets.UTF_8);
        String eventType = extractEventType(rawPayload);

        DemoWebhookEvent event = DemoWebhookEvent.verified(merchantId, eventType, payloadJson);
        eventStore.addEvent(merchantId, event);

        log.info("Demo webhook successfully verified and captured: eventId={}, merchantId={}, eventType={}",
                event.id(), merchantId, eventType);

        return ResponseEntity.ok(new DemoWebhookReceiverResponse(
                "VERIFIED",
                event.id(),
                merchantId,
                event.receivedAt(),
                "Webhook successfully verified and captured in demo event store"
        ));
    }

    /**
     * Dashboard polling endpoint returning recent verified events for the authenticated merchant.
     *
     * @param userIdHeader     {@code X-User-Id} injected by API Gateway perimeter filter (verified user UUID)
     * @param merchantIdHeader optional candidate {@code X-Merchant-Id} header (anti-spoof checked)
     * @param userRoleHeader   {@code X-User-Role} injected by API Gateway perimeter filter
     * @return list of recent demo events for the caller
     */
    @GetMapping(value = "/demo-events", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getDemoEvents(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-Merchant-Id", required = false) String merchantIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRoleHeader) {

        // 1. Authenticate Caller Identity from Gateway X-User-Id
        UUID userId = parseAuthenticatedUserId(userIdHeader);
        if (userId == null) {
            log.warn("Rejected unauthenticated request to GET /demo-events: missing or invalid X-User-Id");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "UNAUTHENTICATED",
                            "message", "Missing trusted caller identity header (X-User-Id)"));
        }

        // 2. Enforce Role-Based Access Control
        if (!isAuthorizedRole(userRoleHeader)) {
            log.warn("Rejected unauthorized role [{}] access to GET /demo-events for userId [{}]",
                    userRoleHeader, userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "FORBIDDEN",
                            "message", "Access restricted to merchants and administrators"));
        }

        // 3. Resolve Verified Merchant ID via Trusted Mapping (Fail-Closed)
        Optional<UUID> verifiedMerchantOpt = mappingResolver.resolveMerchantId(userId);
        if (verifiedMerchantOpt.isEmpty()) {
            log.warn("GET /demo-events failed closed: no trusted user-to-merchant mapping available for userId={}", userId);
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
            log.warn("Rejected forged merchant identity on GET /demo-events: caller claims merchantId={}, but user maps to verified {}",
                    merchantIdHeader, verifiedMerchantId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "FORGED_MERCHANT_IDENTITY",
                            "message", "Caller-supplied X-Merchant-Id does not match verified merchant identity for authenticated user"));
        }

        // 5. Retrieve Isolated Events
        List<DemoWebhookEvent> events = eventStore.getRecentEvents(verifiedMerchantId);
        log.debug("Retrieved {} demo webhook events for verifiedMerchantId={}", events.size(), verifiedMerchantId);

        return ResponseEntity.ok(new DemoWebhookEventsResponse(verifiedMerchantId, events.size(), events));
    }

    /**
     * Resets the demo events buffer for the authenticated merchant.
     */
    @DeleteMapping(value = "/demo-events", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> clearDemoEvents(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-Merchant-Id", required = false) String merchantIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String userRoleHeader) {

        // 1. Authenticate Caller Identity from Gateway X-User-Id
        UUID userId = parseAuthenticatedUserId(userIdHeader);
        if (userId == null) {
            log.warn("Rejected unauthenticated request to DELETE /demo-events: missing or invalid X-User-Id");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "UNAUTHENTICATED",
                            "message", "Missing trusted caller identity header (X-User-Id)"));
        }

        // 2. Enforce Role-Based Access Control (Fixes missing RBAC vulnerability)
        if (!isAuthorizedRole(userRoleHeader)) {
            log.warn("Rejected unauthorized role [{}] access to DELETE /demo-events for userId [{}]",
                    userRoleHeader, userId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "FORBIDDEN",
                            "message", "Access restricted to merchants and administrators"));
        }

        // 3. Resolve Verified Merchant ID via Trusted Mapping (Fail-Closed)
        Optional<UUID> verifiedMerchantOpt = mappingResolver.resolveMerchantId(userId);
        if (verifiedMerchantOpt.isEmpty()) {
            log.warn("DELETE /demo-events failed closed: no trusted user-to-merchant mapping available for userId={}", userId);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "AUTH_MAPPING_UNAVAILABLE",
                            "message", "Trusted user-to-merchant mapping is unavailable. "
                                    + "User IDs and Merchant IDs are separate identifiers. "
                                    + "Failing closed to prevent unauthorized merchant impersonation."));
        }

        UUID verifiedMerchantId = verifiedMerchantOpt.get();

        // 4. Anti-Spoofing Check: Caller-supplied X-Merchant-Id must not contradict verified mapping
        if (isForgedMerchantIdentity(merchantIdHeader, verifiedMerchantId)) {
            log.warn("Rejected forged merchant identity on DELETE /demo-events: caller claims merchantId={}, but user maps to verified {}",
                    merchantIdHeader, verifiedMerchantId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "FORGED_MERCHANT_IDENTITY",
                            "message", "Caller-supplied X-Merchant-Id does not match verified merchant identity for authenticated user"));
        }

        // 5. Clear Stored Events
        eventStore.clearEvents(verifiedMerchantId);
        log.info("Cleared demo webhook events for verifiedMerchantId={}", verifiedMerchantId);

        return ResponseEntity.ok(Map.of(
                "message", "Demo webhook events cleared successfully",
                "merchantId", verifiedMerchantId
        ));
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

    private UUID resolveCandidateMerchantId(String header1, String header2, String queryParam) {
        String candidate = null;
        if (header1 != null && !header1.isBlank()) {
            candidate = header1.trim();
        } else if (header2 != null && !header2.isBlank()) {
            candidate = header2.trim();
        } else if (queryParam != null && !queryParam.isBlank()) {
            candidate = queryParam.trim();
        } else if (!fallbackDemoMerchantId.isBlank()) {
            candidate = fallbackDemoMerchantId;
        }

        if (candidate == null) {
            return null;
        }

        try {
            return UUID.fromString(candidate);
        } catch (IllegalArgumentException ex) {
            log.warn("Candidate merchantId is not a valid UUID: candidate={}", candidate);
            return null;
        }
    }

    private String resolveSigningSecret(UUID merchantId) {
        // 1. Attempt lookup via Auth Service internal client
        try {
            Optional<MerchantWebhookConfigResponse> configOpt = authInternalClient.getMerchantWebhookConfig(merchantId);
            if (configOpt.isPresent() && configOpt.get().webhookSecret() != null) {
                return configOpt.get().webhookSecret();
            }
        } catch (Exception ex) {
            log.warn("Failed to retrieve merchant webhook secret from Auth Service: merchantId={}, reason={}",
                    merchantId, ex.getMessage());
        }

        // 2. Strict Dev/Demo Fallback: Disabled in production, requires explicit matching merchant ID
        if (isProductionEnvironment()) {
            log.debug("Demo fallback secret is disabled in production environment");
            return null;
        }

        // Never allow a global fallback secret for arbitrary merchants: both secret and merchant ID must be non-blank
        if (!fallbackDemoSecret.isBlank() && !fallbackDemoMerchantId.isBlank()) {
            if (fallbackDemoMerchantId.equalsIgnoreCase(merchantId.toString())) {
                log.info("Using configured demo fallback secret for explicit demo merchantId={}", merchantId);
                return fallbackDemoSecret;
            } else {
                log.warn("Demo fallback secret rejected: merchantId={} does not match explicit demo merchantId={}",
                        merchantId, fallbackDemoMerchantId);
            }
        }

        return null;
    }

    private boolean isProductionEnvironment() {
        if (environment == null) {
            return false;
        }
        return environment.matchesProfiles("prod", "production");
    }

    private String extractEventType(byte[] payloadBytes) {
        try {
            JsonNode root = objectMapper.readTree(payloadBytes);
            if (root.hasNonNull("eventType")) {
                return root.get("eventType").asText();
            }
        } catch (Exception ignored) {
            // Non-JSON or missing eventType falls back to default
        }
        return "parcel.status.updated";
    }
}
