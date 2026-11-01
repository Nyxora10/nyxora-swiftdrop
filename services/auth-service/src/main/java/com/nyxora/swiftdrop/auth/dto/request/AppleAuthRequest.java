package com.nyxora.swiftdrop.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for Apple Sign In token exchange.
 * Note: Apple only returns the user's name on the initial sign-in from the
 * client,
 * so fullName is accepted as an optional claim forwarded by the frontend.
 */
public record AppleAuthRequest(
                @NotBlank(message = "Apple identity token is required") String identityToken,

                String fullName) {
}
