package com.nyxora.swiftdrop.notification.webhook.dto;

import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO returned by POST /api/v1/webhooks/logs/{id}/retry upon manual retry request acceptance (Task 3.6).
 *
 * @param status         Outcome status (e.g. "ACCEPTED", "REJECTED")
 * @param deliveryLogId  Identifier of the retried webhook log
 * @param merchantId     Owning merchant UUID
 * @param previousStatus State before manual retry
 * @param newStatus      New state after retry scheduling
 * @param scheduledAt    Timestamp when manual retry was scheduled
 * @param message        Human-readable summary
 */
public record ManualRetryResponse(
        String status,
        UUID deliveryLogId,
        UUID merchantId,
        WebhookDeliveryStatus previousStatus,
        WebhookDeliveryStatus newStatus,
        Instant scheduledAt,
        String message
) {
    public static ManualRetryResponse accepted(
            UUID deliveryLogId,
            UUID merchantId,
            WebhookDeliveryStatus previousStatus,
            WebhookDeliveryStatus newStatus,
            String message) {
        return new ManualRetryResponse(
                "ACCEPTED",
                deliveryLogId,
                merchantId,
                previousStatus,
                newStatus,
                Instant.now(),
                message
        );
    }
}

