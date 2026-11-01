package com.nyxora.swiftdrop.auth.service;

import com.nyxora.swiftdrop.auth.entity.User;

import java.util.UUID;

/**
 * Service managing the lifecycle, rotation, and cryptographic hashing of
 * persistent Refresh Tokens.
 */
public interface RefreshTokenService {

    /**
     * Issues a new Refresh Token for a specified User.
     *
     * @param user User entity
     * @return Raw unhashed token string to return to the client
     */
    String createRefreshToken(User user);

    /**
     * Verifies the authenticity and validity of a refresh token string,
     * marks it as revoked, and issues a fresh rotated token.
     *
     * @param rawToken Incoming refresh token string from client
     * @return New raw refresh token string
     */
    RotatedToken verifyAndRotate(String rawToken);

    /**
     * Revokes all active refresh tokens for a user (used during logout).
     *
     * @param userId User UUID
     */
    void revokeAllByUserId(UUID userId);

    /**
     * Container holding the verified User and newly rotated token string.
     */
    record RotatedToken(User user, String newRefreshToken) {
    }
}
