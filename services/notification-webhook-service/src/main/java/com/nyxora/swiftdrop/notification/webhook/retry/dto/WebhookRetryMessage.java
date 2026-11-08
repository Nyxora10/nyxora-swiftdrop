package com.nyxora.swiftdrop.notification.webhook.retry.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.UUID;

/**
 * Serializable message payload routed through RabbitMQ DLQ backoff queues (Task 3.4).
 *
 * <p>Preserves delivery audit identity and the exact original JSON payload across
 * retry attempts to guarantee cryptographic byte-identity during subsequent HMAC signing.</p>
 *
 * <p>Security & Privacy:
 * <ul>
 *   <li>Contains no merchant signing secrets, credentials, or internal tokens.</li>
 *   <li>Secrets are resolved on-demand per dispatch attempt from the 15-minute cache.</li>
 * </ul>
 *
 * @param deliveryLogId Unique identifier of the persisted {@code WebhookDeliveryLog} row
 * @param merchantId    UUID of the target merchant
 * @param parcelId      UUID of the affected parcel
 * @param eventType     Domain event classification (e.g. {@code "parcel.status.updated"})
 * @param payload       Original byte-exact JSON payload string for HMAC signing
 * @param retryCount    The retry sequence number (1 through 5)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookRetryMessage(
        @JsonProperty("deliveryLogId") UUID deliveryLogId,
        @JsonProperty("merchantId") UUID merchantId,
        @JsonProperty("parcelId") UUID parcelId,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("payload") String payload,
        @JsonProperty("retryCount") int retryCount
) implements Serializable {

    public WebhookRetryMessage {
        if (deliveryLogId == null) {
            throw new IllegalArgumentException("deliveryLogId must not be null");
        }
        if (merchantId == null) {
            throw new IllegalArgumentException("merchantId must not be null");
        }
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("payload must not be null or blank");
        }
    }
}

