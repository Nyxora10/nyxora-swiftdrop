package com.nyxora.swiftdrop.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for renewing an expired Access Token using a persistent Refresh Token.
 */
public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token string is required")
        String refreshToken
) {}

