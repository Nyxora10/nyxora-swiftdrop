package com.nyxora.swiftdrop.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Internal Merchant Webhook Configuration Response for notification-webhook-service")
public record MerchantWebhookConfigResponse(
        @Schema(description = "Unique Merchant UUID", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
        UUID merchantId,

        @Schema(description = "Registered outbound webhook URL", example = "https://merchant.example.com/api/webhooks/swiftdrop")
        String webhookUrl,

        @Schema(description = "Shared HMAC-SHA256 signing secret for payload verification", example = "whsec_live_9a8b7c6d5e4f3a2b1c")
        String webhookSecret,

        @Schema(description = "Indicates whether the merchant account is currently active", example = "true")
        Boolean isActive
) {}
