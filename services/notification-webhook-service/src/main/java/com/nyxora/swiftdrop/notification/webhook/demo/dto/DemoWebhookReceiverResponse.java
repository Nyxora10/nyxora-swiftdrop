package com.nyxora.swiftdrop.notification.webhook.demo.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Structured response returned by POST /api/v1/webhooks/demo-receiver upon receipt and verification.
 *
 * @param status     Outcome status (e.g. "ACCEPTED", "VERIFIED")
 * @param eventId    Unique identifier of the stored demo event
 * @param merchantId UUID of the merchant whose signing secret was verified
 * @param receivedAt Timestamp of event acceptance
 * @param message    Human-readable description
 */
public record DemoWebhookReceiverResponse(
        String status,
        UUID eventId,
        UUID merchantId,
        Instant receivedAt,
        String message
) {
}

