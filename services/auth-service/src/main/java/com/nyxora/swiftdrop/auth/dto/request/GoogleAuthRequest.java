package com.nyxora.swiftdrop.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload submitted by frontend clients containing the signed Google OIDC ID Token.
 */
public record GoogleAuthRequest(
        @NotBlank(message = "Google ID token is required")
        String idToken
) {}

