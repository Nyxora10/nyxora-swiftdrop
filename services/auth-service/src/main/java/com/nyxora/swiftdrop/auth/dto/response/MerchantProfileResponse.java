package com.nyxora.swiftdrop.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Merchant Self-Service Profile & API Configuration Response")
public record MerchantProfileResponse(
        @Schema(description = "Merchant identifier UUID", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
        UUID merchantId,

        @Schema(description = "Registered registered business/company name", example = "Daraz Sri Lanka (Pvt) Ltd")
        String businessName,

        @Schema(description = "Government business registration number", example = "PV-12345")
        String registrationNumber,

        @Schema(description = "OAuth 2.0 Client ID for M2M API token requests", example = "mer_daraz_lk_89f2")
        String oauthClientId,

        @Schema(description = "Current outbound webhook endpoint URL", example = "https://partner.daraz.lk/webhooks/swiftdrop")
        String webhookUrl,

        @Schema(description = "Masked HMAC webhook signing secret", example = "whsec_****1a2b")
        String webhookSecret,

        @Schema(description = "Full HMAC webhook signing secret. Present only in the response of the request that "
                + "generated it; never returned by profile lookups. Store it immediately.",
                example = "whsec_3f9c0d4e5a6b7c8d9e0f1a2b3c4d5e6f")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String newWebhookSecret
) {
    /** Ordinary profile view: masked secret only. */
    public MerchantProfileResponse(UUID merchantId, String businessName, String registrationNumber,
                                   String oauthClientId, String webhookUrl, String webhookSecret) {
        this(merchantId, businessName, registrationNumber, oauthClientId, webhookUrl, webhookSecret, null);
    }
}
