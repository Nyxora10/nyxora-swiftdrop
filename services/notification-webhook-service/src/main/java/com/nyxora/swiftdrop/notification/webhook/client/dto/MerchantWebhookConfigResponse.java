package com.nyxora.swiftdrop.notification.webhook.client.dto;

import java.util.UUID;

/**
 * Data Transfer Object representing the merchant webhook configuration retrieved from auth-service.
 *
 * <p>Intended Endpoint contract (Member 2, Task 2.1):
 * {@code GET /api/v1/auth/internal/merchants/{merchantId}/webhook-config}
 *
 * <p>Adheres to the Zero Cross-Database Query Rule:
 * Allows notification-webhook-service to retrieve the target destination URL and the private HMAC-SHA256
 * signing secret for a merchant without directly accessing {@code auth_db}.
 *
 * @param merchantId    UUID of the merchant
 * @param webhookUrl    Destination HTTP/HTTPS endpoint URL configured by the merchant
 * @param webhookSecret Private shared secret used to compute {@code X-SwiftDrop-Signature}
 * @param isActive      Whether the merchant account is currently active and eligible for webhooks
 */
public record MerchantWebhookConfigResponse(
        UUID merchantId,
        String webhookUrl,
        String webhookSecret,
        Boolean isActive
) {
    /**
     * Checks if this configuration is complete and eligible for outbound webhook dispatch.
     *
     * @return true if active, webhookUrl is present and non-blank, and webhookSecret is present and non-blank
     */
    public boolean isEligibleForDispatch() {
        return Boolean.TRUE.equals(isActive)
                && webhookUrl != null && !webhookUrl.isBlank()
                && webhookSecret != null && !webhookSecret.isBlank();
    }
}

