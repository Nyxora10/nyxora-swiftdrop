package com.nyxora.swiftdrop.notification.webhook.demo.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object representing an inbound webhook event captured by the built-in demo receiver.
 *
 * <p>Exposed to the Next.js merchant dashboard for live inspection during examiner demonstrations.
 * Security Note: Webhook signing secrets and raw signature digests are strictly excluded
 * to prevent credential leakage to client applications.</p>
 *
 * @param id                 Unique identifier for the received event
 * @param merchantId         UUID of the merchant to whom this event belongs
 * @param eventType          Domain event type (e.g. "parcel.status.updated")
 * @param payload            The verbatim raw JSON payload received in the HTTP request body
 * @param receivedAt         UTC timestamp when the event was received and verified
 * @param verified           Whether the event successfully passed HMAC-SHA256 signature verification
 * @param verificationStatus Human-readable verification outcome summary (e.g. "VERIFIED_HMAC_SHA256")
 */
public record DemoWebhookEvent(
        UUID id,
        UUID merchantId,
        String eventType,
        String payload,
        Instant receivedAt,
        boolean verified,
        String verificationStatus
) {
    /**
     * Factory method creating a successfully verified demo webhook event record.
     *
     * @param merchantId UUID of the merchant
     * @param eventType  domain event identifier
     * @param payload    raw JSON payload string
     * @return initialized DemoWebhookEvent
     */
    public static DemoWebhookEvent verified(UUID merchantId, String eventType, String payload) {
        return new DemoWebhookEvent(
                UUID.randomUUID(),
                merchantId,
                eventType != null && !eventType.isBlank() ? eventType : "webhook.event",
                payload,
                Instant.now(),
                true,
                "VERIFIED_HMAC_SHA256"
        );
    }
}

