package com.nyxora.swiftdrop.notification.webhook.dto;

import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryLog;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe, sanitized Data Transfer Object exposing webhook delivery log metadata (Task 3.6).
 *
 * <p><strong>Security Note:</strong>
 * Strictly excludes sensitive authentication tokens, internal webhook secrets, and private signing material.
 * Only non-sensitive audit attributes supported by the underlying {@code webhook_delivery_logs} entity are exposed.</p>
 *
 * @param id             Unique identifier of the delivery log
 * @param parcelId       Associated parcel identifier
 * @param merchantId     Owning merchant UUID
 * @param eventType      Domain event type (e.g. "parcel.status.updated")
 * @param httpStatusCode HTTP response status code received from destination (if attempted)
 * @param retryCount     Number of retries executed
 * @param deliveryStatus Current delivery lifecycle status
 * @param nextRetryAt    Timestamp for the next scheduled retry attempt (if active)
 * @param createdAt      Timestamp when the delivery attempt was initially created
 */
public record WebhookDeliveryLogResponse(
        UUID id,
        UUID parcelId,
        UUID merchantId,
        String eventType,
        Integer httpStatusCode,
        Integer retryCount,
        WebhookDeliveryStatus deliveryStatus,
        Instant nextRetryAt,
        Instant createdAt
) {
    /**
     * Maps an internal JPA entity to a safe, read-only DTO.
     *
     * @param log the entity to map
     * @return initialized response record
     */
    public static WebhookDeliveryLogResponse fromEntity(WebhookDeliveryLog log) {
        if (log == null) {
            return null;
        }
        return new WebhookDeliveryLogResponse(
                log.getId(),
                log.getParcelId(),
                log.getMerchantId(),
                log.getEventType(),
                log.getHttpStatusCode(),
                log.getRetryCount(),
                log.getDeliveryStatus(),
                log.getNextRetryAt(),
                log.getCreatedAt()
        );
    }
}

