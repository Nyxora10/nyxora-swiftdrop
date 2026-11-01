package com.nyxora.swiftdrop.auth.service;

import com.nyxora.swiftdrop.auth.dto.request.*;
import com.nyxora.swiftdrop.auth.dto.response.AuthResponse;
import com.nyxora.swiftdrop.auth.dto.response.RegistrationResponse;
import com.nyxora.swiftdrop.auth.dto.response.UserProfileResponse;

import java.util.UUID;

/**
 * Service contract defining identity, registration, credential validation,
 * and session lifecycle workflows for SwiftDrop.
 */
public interface AuthService {

    /**
     * Registers a retail customer and returns active authentication tokens immediately.
     */
    AuthResponse registerCustomer(CustomerRegisterRequest request);

    /**
     * Registers a merchant partner requiring administrator approval.
     */
    RegistrationResponse registerMerchant(MerchantRegisterRequest request);

    /**
     * Registers a delivery courier requiring administrator approval.
     */
    RegistrationResponse registerCourier(CourierRegisterRequest request);

    /**
     * Authenticates user credentials and checks account status.
     * Returns Access and Refresh tokens on success.
     */
    AuthResponse login(LoginRequest request);

    /**
     * Authenticates a user via Google OAuth 2.0 ID Token.
     * Auto-registers new customers or logs in existing linked accounts.
     */
    AuthResponse authenticateGoogle(GoogleAuthRequest request);

    /**
     * Exchanges a valid refresh token for a fresh access token and rotates the refresh token.
     */
    AuthResponse refreshToken(RefreshTokenRequest request);

    /**
     * Terminates active session and invalidates all refresh tokens for the user.
     */
    void logout(UUID currentUserId);

    /**
     * Retrieves full profile information for the authenticated user.
     */
    UserProfileResponse getCurrentUserProfile(UUID currentUserId);
}
