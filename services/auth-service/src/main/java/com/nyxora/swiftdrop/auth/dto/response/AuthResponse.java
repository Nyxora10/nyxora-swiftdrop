package com.nyxora.swiftdrop.auth.dto.response;

import java.util.UUID;

/**
 * Successful authentication response containing JWT tokens and basic identity context.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UUID userId,
        String email,
        String role,
        String fullName
) {
    public static AuthResponse of(String accessToken,
                                  String refreshToken,
                                  long expiresIn,
                                  UUID userId,
                                  String email,
                                  String role,
                                  String fullName) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn, userId, email, role, fullName);
    }
}

