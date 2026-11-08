package com.nyxora.swiftdrop.notification.webhook.demo.dto;

import java.util.List;
import java.util.UUID;

/**
 * Structured response returned by GET /api/v1/webhooks/demo-events for dashboard polling.
 *
 * @param merchantId UUID of the authenticated merchant
 * @param count      Number of recent demo events returned
 * @param events     List of verified demo webhook events (newest first)
 */
public record DemoWebhookEventsResponse(
        UUID merchantId,
        int count,
        List<DemoWebhookEvent> events
) {
}

