package com.nyxora.swiftdrop.notification.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.notification.webhook.client.AuthInternalClient;
import com.nyxora.swiftdrop.notification.webhook.client.dto.MerchantWebhookConfigResponse;
import com.nyxora.swiftdrop.notification.webhook.dto.WebhookDispatchResult;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryLog;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;
import com.nyxora.swiftdrop.notification.webhook.repository.WebhookDeliveryLogRepository;
import com.nyxora.swiftdrop.notification.webhook.util.HmacSignatureUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Core outbound webhook dispatch engine (Task 3.3).
 *
 * <p>
 * Responsibilities:
 * <ul>
 * <li>Retrieves merchant webhook destination URL and secret from
 * {@link AuthInternalClient}.</li>
 * <li>Enforces anti-SSRF address validation to prevent internal network
 * probing.</li>
 * <li>Serializes event payload to JSON exactly once using Jackson
 * {@link ObjectMapper}.</li>
 * <li>Calculates HMAC-SHA256 signature over exact request bytes via
 * {@link HmacSignatureUtil}.</li>
 * <li>Executes HTTP POST using Spring {@link RestClient} with strict 5-second
 * timeouts and no auto-redirects.</li>
 * <li>Persists audit log into {@code webhook_delivery_logs} table via
 * {@link WebhookDeliveryLogRepository}.</li>
 * <li>Classifies failures into retryable and non-retryable outcomes for the
 * RabbitMQ retry engine (Task 3.4).</li>
 * </ul>
 *
 * <p>
 * Security Contract:
 * <ul>
 * <li>Never logs merchant signing secrets or calculated cryptographic
 * signatures.</li>
 * <li>Sanitizes error logs and messages.</li>
 * </ul>
 */
@Service
public class WebhookDispatcherService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcherService.class);

    private static final Duration TIMEOUT_DURATION = Duration.ofSeconds(5);

    private final AuthInternalClient authInternalClient;
    private final WebhookDeliveryLogRepository logRepository;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final boolean allowLocalhost;

    @Autowired
    public WebhookDispatcherService(
            AuthInternalClient authInternalClient,
            WebhookDeliveryLogRepository logRepository,
            @Autowired(required = false) ObjectMapper objectMapper,
            @Value("${app.webhook.allow-localhost:false}") boolean allowLocalhost) {
        this.authInternalClient = authInternalClient;
        this.logRepository = logRepository;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.allowLocalhost = allowLocalhost;

        // Configure standard Java HTTP Client with 5-second connection timeout and
        // disabled redirects
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT_DURATION)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(TIMEOUT_DURATION);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Testing constructor allowing injection of a custom RestClient.
     */
    public WebhookDispatcherService(
            AuthInternalClient authInternalClient,
            WebhookDeliveryLogRepository logRepository,
            ObjectMapper objectMapper,
            RestClient restClient,
            boolean allowLocalhost) {
        this.authInternalClient = authInternalClient;
        this.logRepository = logRepository;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.restClient = restClient;
        this.allowLocalhost = allowLocalhost;
    }

    /**
     * Executes the initial webhook dispatch attempt for a merchant event.
     *
     * @param merchantId   UUID of the target merchant
     * @param parcelId     UUID of the affected parcel
     * @param eventType    Event classification (e.g.
     *                     {@code "parcel.status.updated"})
     * @param eventPayload Raw event payload object or JSON string
     * @return {@link WebhookDispatchResult} detailing dispatch outcome and retry
     *         decision
     */
    public WebhookDispatchResult dispatchWebhook(
            UUID merchantId,
            UUID parcelId,
            String eventType,
            Object eventPayload) {

        if (merchantId == null) {
            log.warn("Cannot dispatch webhook: merchantId is null for parcelId={}", parcelId);
            return WebhookDispatchResult.permanentFailure(null, "INVALID_MERCHANT_ID", "Merchant ID is missing", null);
        }

        // 1. Serialize payload to JSON exactly once
        String jsonPayload;
        byte[] payloadBytes;
        try {
            if (eventPayload instanceof String strPayload) {
                jsonPayload = strPayload;
            } else {
                jsonPayload = objectMapper.writeValueAsString(eventPayload);
            }
            payloadBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.error("Failed to serialize webhook payload to JSON for merchantId={}, parcelId={}", merchantId,
                    parcelId, ex);
            return WebhookDispatchResult.permanentFailure(null, "PAYLOAD_SERIALIZATION_ERROR",
                    "Unable to serialize payload", null);
        }

        // 2. Persist initial audit log in PENDING status with retryCount = 0
        WebhookDeliveryLog deliveryLog = new WebhookDeliveryLog();
        deliveryLog.setMerchantId(merchantId);
        deliveryLog.setParcelId(parcelId != null ? parcelId : UUID.randomUUID());
        deliveryLog.setEventType(eventType != null ? eventType : "unknown.event");
        deliveryLog.setPayload(jsonPayload);
        deliveryLog.setRetryCount(0);
        deliveryLog.setDeliveryStatus(WebhookDeliveryStatus.PENDING);
        deliveryLog = logRepository.save(deliveryLog);

        return executeDelivery(deliveryLog, payloadBytes);
    }

    /**
     * Executes a retry dispatch attempt for an existing persisted webhook delivery
     * log.
     * Reuses the existing log row in the database and the original JSON payload
     * bytes,
     * updating the audit record in place without creating duplicate database rows.
     *
     * @param deliveryLog the existing persisted delivery log entity
     * @return {@link WebhookDispatchResult} detailing dispatch outcome
     * @throws IllegalArgumentException if deliveryLog is null or unpersisted
     */
    public WebhookDispatchResult dispatchRetryWebhook(WebhookDeliveryLog deliveryLog) {
        if (deliveryLog == null || deliveryLog.getId() == null) {
            throw new IllegalArgumentException("Cannot dispatch retry for null or unpersisted delivery log");
        }
        if (deliveryLog.getPayload() == null || deliveryLog.getPayload().isBlank()) {
            log.error("Cannot dispatch retry: payload is missing for deliveryLogId={}", deliveryLog.getId());
            return WebhookDispatchResult.permanentFailure(null, "MISSING_PAYLOAD", "Delivery log has no payload",
                    deliveryLog);
        }

        byte[] payloadBytes = deliveryLog.getPayload().getBytes(StandardCharsets.UTF_8);
        return executeDelivery(deliveryLog, payloadBytes);
    }

    /**
     * Overload: looks up an existing delivery log by its ID and executes a retry
     * dispatch.
     *
     * @param deliveryLogId unique identifier of the delivery log
     * @return {@link WebhookDispatchResult} detailing dispatch outcome
     */
    public WebhookDispatchResult dispatchRetryWebhook(UUID deliveryLogId) {
        if (deliveryLogId == null) {
            throw new IllegalArgumentException("deliveryLogId must not be null");
        }
        Optional<WebhookDeliveryLog> logOpt = logRepository.findById(deliveryLogId);
        if (logOpt.isEmpty()) {
            log.warn("Delivery log not found for retry dispatch: deliveryLogId={}", deliveryLogId);
            return WebhookDispatchResult.permanentFailure(null, "LOG_NOT_FOUND", "Delivery log not found", null);
        }
        return dispatchRetryWebhook(logOpt.get());
    }

    /**
     * Internal delivery executor shared between initial dispatches and DLQ retry
     * dispatches.
     */
    private WebhookDispatchResult executeDelivery(WebhookDeliveryLog deliveryLog, byte[] payloadBytes) {
        UUID merchantId = deliveryLog.getMerchantId();
        UUID parcelId = deliveryLog.getParcelId();
        String eventType = deliveryLog.getEventType();

        // 1. Retrieve merchant webhook destination and signing secret
        Optional<MerchantWebhookConfigResponse> configOpt = authInternalClient.getMerchantWebhookConfig(merchantId);
        if (configOpt.isEmpty() || !configOpt.get().isEligibleForDispatch()) {
            /*
             * Note on AuthInternalClient limitation:
             * AuthInternalClient returns Optional.empty() both when a merchant is genuinely
             * unconfigured (404)
             * and when auth-service encounters transient 5xx or network errors.
             * To prevent silently dropping retries due to transient Auth Service outages,
             * we treat this
             * as retryable by default so the DLQ retry engine can re-attempt.
             */
            log.warn("Merchant webhook configuration unavailable for merchantId={}, parcelId={}. Flagged for retry.",
                    merchantId, parcelId);
            deliveryLog.setDeliveryStatus(WebhookDeliveryStatus.RETRYING);
            deliveryLog = logRepository.save(deliveryLog);

            return WebhookDispatchResult.retryableFailure(
                    null,
                    "AUTH_CONFIG_UNAVAILABLE",
                    "Merchant webhook configuration unavailable from auth-service",
                    deliveryLog);
        }

        MerchantWebhookConfigResponse config = configOpt.get();
        String targetUrl = config.webhookUrl();
        String secret = config.webhookSecret();

        // 2. Validate URL against SSRF and internal address probing
        if (!isSafeUrl(targetUrl)) {
            log.warn("Blocked potentially unsafe webhook URL for merchantId={}, parcelId={}", merchantId, parcelId);
            // Marked DEAD_LETTER because malformed/unsafe URLs cannot succeed on retry
            deliveryLog.setDeliveryStatus(WebhookDeliveryStatus.DEAD_LETTER);
            deliveryLog.setNextRetryAt(null);
            deliveryLog = logRepository.save(deliveryLog);

            return WebhookDispatchResult.permanentFailure(
                    null,
                    "SSRF_BLOCKED",
                    "Webhook destination URL failed SSRF security validation",
                    deliveryLog);
        }

        // 3. Sign the exact UTF-8 payload bytes
        String signatureHeader;
        try {
            signatureHeader = HmacSignatureUtil.generateSignatureHeader(payloadBytes, secret);
        } catch (Exception ex) {
            log.error("Failed to compute HMAC-SHA256 signature for merchantId={}, parcelId={}", merchantId, parcelId,
                    ex);
            deliveryLog.setDeliveryStatus(WebhookDeliveryStatus.DEAD_LETTER);
            deliveryLog.setNextRetryAt(null);
            deliveryLog = logRepository.save(deliveryLog);

            return WebhookDispatchResult.permanentFailure(
                    null,
                    "SIGNATURE_COMPUTATION_ERROR",
                    "Failed to compute cryptographic signature",
                    deliveryLog);
        }

        // 4. Execute outbound HTTP POST
        log.info("Dispatching signed webhook to merchantId={}, parcelId={}, eventType={}, retryCount={}",
                merchantId, parcelId, eventType, deliveryLog.getRetryCount());

        try {
            var response = restClient.post()
                    .uri(URI.create(targetUrl))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HmacSignatureUtil.SIGNATURE_HEADER_NAME, signatureHeader)
                    .body(payloadBytes)
                    .retrieve()
                    .toBodilessEntity();

            int statusCode = response.getStatusCode().value();
            log.info("Webhook successfully dispatched to merchantId={}, parcelId={}, responseStatus={}, retryCount={}",
                    merchantId, parcelId, statusCode, deliveryLog.getRetryCount());

            deliveryLog.setHttpStatusCode(statusCode);
            deliveryLog.setDeliveryStatus(WebhookDeliveryStatus.DELIVERED);
            deliveryLog.setNextRetryAt(null);
            deliveryLog = logRepository.save(deliveryLog);

            return WebhookDispatchResult.successful(statusCode, deliveryLog);

        } catch (HttpClientErrorException ex) {
            int statusCode = ex.getStatusCode().value();
            log.warn(
                    "Merchant webhook endpoint returned 4xx client error for merchantId={}, parcelId={}, status={}, retryCount={}",
                    merchantId, parcelId, statusCode, deliveryLog.getRetryCount());

            deliveryLog.setHttpStatusCode(statusCode);

            // HTTP 408 (Request Timeout) and 429 (Too Many Requests) are transient and
            // retryable
            if (statusCode == 408 || statusCode == 429) {
                deliveryLog.setDeliveryStatus(WebhookDeliveryStatus.RETRYING);
                deliveryLog = logRepository.save(deliveryLog);
                return WebhookDispatchResult.retryableFailure(
                        statusCode,
                        "HTTP_CLIENT_TIMEOUT_OR_RATE_LIMIT",
                        "Receiver returned status " + statusCode,
                        deliveryLog);
            }

            // Other 4xx errors (e.g. 400 Bad Request, 401 Unauthorized, 404 Not Found) are
            // permanent client errors
            deliveryLog.setDeliveryStatus(WebhookDeliveryStatus.DEAD_LETTER);
            deliveryLog.setNextRetryAt(null);
            deliveryLog = logRepository.save(deliveryLog);
            return WebhookDispatchResult.permanentFailure(
                    statusCode,
                    "HTTP_CLIENT_ERROR",
                    "Receiver returned client error " + statusCode,
                    deliveryLog);

        } catch (HttpServerErrorException ex) {
            int statusCode = ex.getStatusCode().value();
            log.warn(
                    "Merchant webhook endpoint returned 5xx server error for merchantId={}, parcelId={}, status={}, retryCount={}",
                    merchantId, parcelId, statusCode, deliveryLog.getRetryCount());

            deliveryLog.setHttpStatusCode(statusCode);
            deliveryLog.setDeliveryStatus(WebhookDeliveryStatus.RETRYING);
            deliveryLog = logRepository.save(deliveryLog);

            return WebhookDispatchResult.retryableFailure(
                    statusCode,
                    "HTTP_SERVER_ERROR",
                    "Receiver returned server error " + statusCode,
                    deliveryLog);

        } catch (ResourceAccessException ex) {
            log.warn(
                    "Connection timeout or I/O failure reaching merchant webhook endpoint for merchantId={}, parcelId={}, retryCount={}: {}",
                    merchantId, parcelId, deliveryLog.getRetryCount(), ex.getMessage());

            deliveryLog.setHttpStatusCode(null);
            deliveryLog.setDeliveryStatus(WebhookDeliveryStatus.RETRYING);
            deliveryLog = logRepository.save(deliveryLog);

            return WebhookDispatchResult.retryableFailure(
                    null,
                    "CONNECTION_TIMEOUT",
                    "Network connection or read timeout reaching merchant endpoint",
                    deliveryLog);

        } catch (Exception ex) {
            log.error("Unexpected error dispatching webhook for merchantId={}, parcelId={}, retryCount={}: {}",
                    merchantId, parcelId, deliveryLog.getRetryCount(), ex.getMessage(), ex);

            deliveryLog.setHttpStatusCode(null);
            deliveryLog.setDeliveryStatus(WebhookDeliveryStatus.RETRYING);
            deliveryLog = logRepository.save(deliveryLog);

            return WebhookDispatchResult.retryableFailure(
                    null,
                    "UNEXPECTED_ERROR",
                    "Unexpected dispatch exception: " + ex.getClass().getSimpleName(),
                    deliveryLog);
        }
    }

    /**
     * Validates destination URL against SSRF and private-network destinations.
     */
    private boolean isSafeUrl(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(urlString);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return false;
            }

            // Local development override allows http://localhost
            if (allowLocalhost) {
                return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            }

            // Production strictly requires HTTPS
            if (!"https".equalsIgnoreCase(scheme)) {
                return false;
            }

            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }

            // Resolve DNS and verify not internal/loopback/cloud-metadata
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (address.isLoopbackAddress()
                        || address.isSiteLocalAddress()
                        || address.isLinkLocalAddress()
                        || address.isAnyLocalAddress()
                        || isCloudMetadataIp(address.getHostAddress())) {
                    return false;
                }
            }

            return true;

        } catch (UnknownHostException | IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean isCloudMetadataIp(String ip) {
        return "169.254.169.254".equals(ip);
    }
}
