package com.nyxora.swiftdrop.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.URL;

@Schema(description = "Merchant Webhook Configuration Update Request")
public record MerchantWebhookUpdateRequest(
        @Schema(description = "Target HTTPS webhook URL where parcel status updates will be delivered", example = "https://partner.daraz.lk/webhooks/swiftdrop")
        @URL(message = "webhookUrl must be a valid URL format")
        String webhookUrl,

        @Schema(description = "Pass true to regenerate the shared HMAC-SHA256 webhook signing secret", example = "false")
        Boolean regenerateSecret
) {}
