package com.nyxora.swiftdrop.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "OAuth 2.0 Client Credentials Grant Token Request Payload")
public record OAuthTokenRequest(
        @Schema(description = "OAuth 2.0 grant type. Must be 'client_credentials'.", example = "client_credentials", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("grant_type")
        @NotBlank(message = "grant_type is required")
        String grantType,

        @Schema(description = "OAuth 2.0 Client ID issued to merchant", example = "mer_daraz_lk_89f2", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("client_id")
        @NotBlank(message = "client_id is required")
        String clientId,

        @Schema(description = "OAuth 2.0 Client Secret", example = "cs_live_9a8b7c6d5e4f3a2b1c", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("client_secret")
        @NotBlank(message = "client_secret is required")
        String clientSecret
) {}
