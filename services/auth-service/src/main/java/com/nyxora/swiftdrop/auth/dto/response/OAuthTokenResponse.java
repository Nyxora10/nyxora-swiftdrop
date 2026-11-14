package com.nyxora.swiftdrop.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nyxora.swiftdrop.auth.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Standard OAuth 2.0 Bearer Token Response")
public record OAuthTokenResponse(
        @Schema(description = "Stateless JWT Bearer Access Token", example = "eyJhbGciOiJIUzI1NiIsIn...")
        @JsonProperty("access_token")
        String accessToken,

        @Schema(description = "Token Type. Always 'Bearer'.", example = "Bearer")
        @JsonProperty("token_type")
        String tokenType,

        @Schema(description = "Access token lifetime duration in seconds", example = "900")
        @JsonProperty("expires_in")
        long expiresIn,

        @Schema(description = "Authorized permission scopes", example = "parcels:create parcels:bulk")
        @JsonProperty("scope")
        String scope,

        @Schema(description = "Unique Merchant UUID", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
        @JsonProperty("merchant_id")
        UUID merchantId
) {
    public static OAuthTokenResponse of(String accessToken, long expiresInMs, UUID merchantId) {
        return new OAuthTokenResponse(
                accessToken,
                "Bearer",
                expiresInMs / 1000,
                JwtTokenProvider.M2M_SCOPES,
                merchantId
        );
    }
}
