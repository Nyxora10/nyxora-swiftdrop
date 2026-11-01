package com.nyxora.swiftdrop.auth.controller;

import com.nyxora.swiftdrop.auth.dto.request.AppleAuthRequest;
import com.nyxora.swiftdrop.auth.dto.request.CourierRegisterRequest;
import com.nyxora.swiftdrop.auth.dto.request.CustomerRegisterRequest;
import com.nyxora.swiftdrop.auth.dto.request.FacebookAuthRequest;
import com.nyxora.swiftdrop.auth.dto.request.GoogleAuthRequest;
import com.nyxora.swiftdrop.auth.dto.request.LoginRequest;
import com.nyxora.swiftdrop.auth.dto.request.MerchantRegisterRequest;
import com.nyxora.swiftdrop.auth.dto.request.RefreshTokenRequest;
import com.nyxora.swiftdrop.auth.dto.response.AuthResponse;
import com.nyxora.swiftdrop.auth.dto.response.RegistrationResponse;
import com.nyxora.swiftdrop.auth.dto.response.UserProfileResponse;
import com.nyxora.swiftdrop.auth.security.UserPrincipal;
import com.nyxora.swiftdrop.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public and authenticated REST endpoints for identity, registration,
 * credential verification, and session management.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication & Identity", description = "Endpoints for user registration, local login, OAuth 2.0 social logins (Google, Apple, Facebook), token refresh, and session management.")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register/customer")
    @Operation(summary = "Register Customer", description = "Self-service onboarding for parcel senders and recipients. Automatically active with immediate JWT issuance.")
    public ResponseEntity<AuthResponse> registerCustomer(@Valid @RequestBody CustomerRegisterRequest request) {
        AuthResponse response = authService.registerCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/register/merchant")
    @Operation(summary = "Register Merchant Partner", description = "Business partner onboarding. Requires business registration and enters PENDING_APPROVAL status until reviewed by an administrator.")
    public ResponseEntity<RegistrationResponse> registerMerchant(@Valid @RequestBody MerchantRegisterRequest request) {
        RegistrationResponse response = authService.registerMerchant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/register/courier")
    @Operation(summary = "Register Courier Driver", description = "Courier driver onboarding. Requires driver license and vehicle type, entering PENDING_APPROVAL status until reviewed by an administrator.")
    public ResponseEntity<RegistrationResponse> registerCourier(@Valid @RequestBody CourierRegisterRequest request) {
        RegistrationResponse response = authService.registerCourier(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Local Email & Password Login", description = "Authenticates local credentials, enforces account lifecycle rules (blocks PENDING_APPROVAL, SUSPENDED, REJECTED), and issues JWT access and refresh tokens.")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/google")
    @Operation(summary = "Google OAuth 2.0 Login", description = "Verifies a Google OIDC ID token against Google's public keys. Auto-provisions new customer accounts or links existing accounts by verified email.")
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody GoogleAuthRequest request) {
        AuthResponse response = authService.authenticateGoogle(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/apple")
    @Operation(summary = "Apple Sign In", description = "Verifies an Apple identity token against Apple's live RSA JWKS keys. Accepts optional fullName provided on initial sign-in.")
    public ResponseEntity<AuthResponse> appleLogin(@Valid @RequestBody AppleAuthRequest request) {
        AuthResponse response = authService.authenticateApple(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/facebook")
    @Operation(summary = "Facebook Login", description = "Verifies a Facebook user access token with Graph API /me using HMAC-SHA256 appsecret_proof spoofing prevention.")
    public ResponseEntity<AuthResponse> facebookLogin(@Valid @RequestBody FacebookAuthRequest request) {
        AuthResponse response = authService.authenticateFacebook(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh JWT Access Token", description = "Rotates single-use refresh token and issues a new access token. Employs token family invalidation upon reuse detection.")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "User Logout", description = "Revokes refresh tokens and terminates the active user session.", security = @SecurityRequirement(name = "BearerAuth"))
    public ResponseEntity<Map<String, String>> logout(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        authService.logout(userPrincipal.getId());
        return ResponseEntity.ok(Map.of("message", "Session invalidated and logged out successfully."));
    }

    @GetMapping("/me")
    @Operation(summary = "Get Current User Profile", description = "Retrieves the authenticated user's ID, email, role, and profile details from their JWT access token.", security = @SecurityRequirement(name = "BearerAuth"))
    public ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        UserProfileResponse response = authService.getCurrentUserProfile(userPrincipal.getId());
        return ResponseEntity.ok(response);
    }
}
