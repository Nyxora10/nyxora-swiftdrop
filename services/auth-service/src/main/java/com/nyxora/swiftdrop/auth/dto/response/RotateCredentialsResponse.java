package com.nyxora.swiftdrop.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Merchant OAuth 2.0 Client Secret Rotation Response")
public record RotateCredentialsResponse(
        @Schema(description = "OAuth 2.0 Client ID", example = "mer_daraz_lk_89f2")
        String oauthClientId,

        @Schema(description = "Newly generated raw plaintext OAuth 2.0 Client Secret (store safely; never displayed again)", example = "cs_live_9a8b7c6d5e4f3a2b1c")
        String oauthClientSecret,

        @Schema(description = "Instructional status notice", example = "Client secret rotated successfully. Store this secret safely as it will not be displayed again.")
        String message
) {}
