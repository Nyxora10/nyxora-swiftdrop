package com.nyxora.swiftdrop.notification.webhook.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.common.security.util.PiiMaskingUtil;
import com.nyxora.swiftdrop.notification.webhook.client.AuthInternalClient;
import com.nyxora.swiftdrop.notification.webhook.client.dto.MerchantWebhookConfigResponse;
import com.nyxora.swiftdrop.notification.webhook.demo.dto.DemoWebhookEvent;
import com.nyxora.swiftdrop.notification.webhook.demo.dto.DemoWebhookEventsResponse;
import com.nyxora.swiftdrop.notification.webhook.demo.dto.DemoWebhookReceiverResponse;
import com.nyxora.swiftdrop.notification.webhook.security.GatewaySecurityValidator;
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
 *       verifies the {@code X-SwiftDrop-Signature} header using HMAC-SHA256, and stores verified payloads. (Public)</li>
 *   <li>{@code GET /api/v1/webhooks/demo-events}: Returns recent verified events for the authenticated merchant
 *       using verified Gateway identity and secret.</li>
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
 *   <li>Validates API Gateway origin via {@code X-Gateway-Secret} before accepting identity headers.</li>
 *   <li>Enforces strict RBAC (ROLE_MERCHANT / ROLE_ADMIN) on both GET and DELETE endpoints.</li>
 *   <li>Rejects caller-supplied {@code X-Merchant-Id} or params if they contradict the verified Gateway identity.</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class DemoWebhookController {

    private static final Logger log = LoggerFactory.getLogger(DemoWebhookController.class);

    private final DemoWebhookEventStore eventStore;
    private final AuthInternalClient authInternalClient;
    private final GatewaySecurityValidator securityValidator;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final String fallbackDemoSecret;
    private final String fallbackDemoMerchantId;

    @Autowired
    public DemoWebhookController(
            DemoWebhookEventStore eventStore,
            AuthInternalClient authInternalClient,
            GatewaySecurityValidator securityValidator,
            @Autowired(required = false) ObjectMapper objectMapper,
            @Autowired(required = false) Environment environment,
            @Value("${app.webhook.demo.secret:}") String fallbackDemoSecret,
            @Value("${app.webhook.demo.merchant-id:}") String fallbackDemoMerchantId) {
        this.eventStore = eventStore;
        this.authInternalClient = authInternalClient;
        this.securityValidator = securityValidator;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.environment = environment;
        this.fallbackDemoSecret = fallbackDemoSecret != null ? fallbackDemoSecret.trim() : "";
        this.fallbackDemoMerchantId = fallbackDemoMerchantId != null ? fallbackDemoMerchantId.trim() : "";
    }

    /**
     * Testing constructor with custom security validator and environment.
     */
    public DemoWebhookController(
            DemoWebhookEventStore eventStore,
            AuthInternalClient authInternalClient,
            ObjectMapper objectMapper,
            GatewaySecurityValidator securityValidator,
            Environment environment,
            String fallbackDemoSecret,
            String fallbackDemoMerchantId) {
        this(eventStore, authInternalClient, securityValidator, objectMapper, environment, fallbackDemoSecret, fallbackDemoMerchantId);
    }

    /**
     * Minimal testing constructor.
     */
    public DemoWebhookController(
            DemoWebhookEventStore eventStore,
            AuthInternalClient authInternalClient,
            ObjectMapper objectMapper) {
        this(eventStore, authInternalClient, new GatewaySecurityValidator(null, GatewaySecurityValidator.DEFAULT_DEV_SECRET), objectMapper, null, null, null);
    }

    /**
     * Deprecated testing constructor for backward compatibility with tests using legacy mapping resolver.
     */
    @Deprecated
    public DemoWebhookController(
            DemoWebhookEventStore eventStore,
            AuthInternalClient authInternalClient,
            ObjectMapper objectMapper,
            UserMerchantMappingResolver mappingResolver,
            Environment environment,
            String fallbackDemoSecret,
            String fallbackDemoMerchantId) {
        this(eventStore, authInternalClient, new GatewaySecurityValidator(environment, GatewaySecurityValidator.DEFAULT_DEV_SECRET), objectMapper, environment, fallbackDemoSecret, fallbackDemoMerchantId);
    }

    /**
     * Inbound receiver endpoint for signed webhook events (Public, secured by HMAC-SHA256 signature).
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
     * @param gatewaySecretHeader    {@code X-Gateway-Secret} injected by API Gateway
     * @param userIdHeader           {@code X-User-Id} injected by API Gateway perimeter filter (verified user UUID)
     * @param merchantIdHeader       {@code X-Merchant-Id} injected by API Gateway perimeter filter
     * @param userRoleHeader         {@code X-User-Role} injected by API Gateway perimeter filter
     * @param candidateMerchantQuery optional candidate {@code merchantId} query parameter (anti-spoof checked)
     * @return list of recent demo events for the caller
     */
    @GetMapping(value = "/demo-events", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getDemoEvents(
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

        // 2. Retrieve Isolated Events
        List<DemoWebhookEvent> events = eventStore.getRecentEvents(verifiedMerchantId);
        log.debug("Retrieved {} demo webhook events for verifiedMerchantId={}", events.size(), verifiedMerchantId);

        return ResponseEntity.ok(new DemoWebhookEventsResponse(verifiedMerchantId, events.size(), events));
    }

    /**
     * Resets the demo events buffer for the authenticated merchant.
     *
     * @param gatewaySecretHeader    {@code X-Gateway-Secret} injected by API Gateway
     * @param userIdHeader           {@code X-User-Id} injected by API Gateway perimeter filter
     * @param merchantIdHeader       {@code X-Merchant-Id} injected by API Gateway perimeter filter
     * @param userRoleHeader         {@code X-User-Role} injected by API Gateway perimeter filter
     * @param candidateMerchantQuery optional candidate {@code merchantId} query parameter (anti-spoof checked)
     * @return clearance confirmation
     */
    @DeleteMapping(value = "/demo-events", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> clearDemoEvents(
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

        // 2. Clear Stored Events
        eventStore.clearEvents(verifiedMerchantId);
        log.info("Cleared demo webhook events for verifiedMerchantId={}", verifiedMerchantId);

        return ResponseEntity.ok(Map.of(
                "message", "Demo webhook events cleared successfully",
                "merchantId", verifiedMerchantId
        ));
    }

    // ─── Private Helper Methods ──────────────────────────────────────────

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
                    merchantId, PiiMaskingUtil.maskText(ex.getMessage()));
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
