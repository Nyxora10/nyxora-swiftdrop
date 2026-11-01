package com.nyxora.swiftdrop.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for Facebook Login OAuth 2.0 token exchange.
 */
public record FacebookAuthRequest(
        @NotBlank(message = "Facebook access token is required")
        String accessToken
) {}
