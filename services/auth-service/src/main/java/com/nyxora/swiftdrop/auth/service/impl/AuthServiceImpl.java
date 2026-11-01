package com.nyxora.swiftdrop.auth.service.impl;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.nyxora.swiftdrop.auth.config.JwtProperties;
import com.nyxora.swiftdrop.auth.dto.SocialUserProfile;
import com.nyxora.swiftdrop.auth.dto.request.*;
import com.nyxora.swiftdrop.auth.dto.response.AuthResponse;
import com.nyxora.swiftdrop.auth.dto.response.RegistrationResponse;
import com.nyxora.swiftdrop.auth.dto.response.UserProfileResponse;
import com.nyxora.swiftdrop.auth.entity.Courier;
import com.nyxora.swiftdrop.auth.entity.Merchant;
import com.nyxora.swiftdrop.auth.entity.Role;
import com.nyxora.swiftdrop.auth.entity.User;
import com.nyxora.swiftdrop.auth.entity.enums.AuthProvider;
import com.nyxora.swiftdrop.auth.entity.enums.RoleName;
import com.nyxora.swiftdrop.auth.entity.enums.UserStatus;
import com.nyxora.swiftdrop.auth.exception.*;
import com.nyxora.swiftdrop.auth.repository.CourierRepository;
import com.nyxora.swiftdrop.auth.repository.MerchantRepository;
import com.nyxora.swiftdrop.auth.repository.RoleRepository;
import com.nyxora.swiftdrop.auth.repository.UserRepository;
import com.nyxora.swiftdrop.auth.security.JwtTokenProvider;
import com.nyxora.swiftdrop.auth.security.UserPrincipal;
import com.nyxora.swiftdrop.auth.security.oauth.AppleIdTokenVerifier;
import com.nyxora.swiftdrop.auth.security.oauth.FacebookClient;
import com.nyxora.swiftdrop.auth.service.AuthService;
import com.nyxora.swiftdrop.auth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final MerchantRepository merchantRepository;
    private final CourierRepository courierRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties jwtProperties;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final AppleIdTokenVerifier appleIdTokenVerifier;
    private final FacebookClient facebookClient;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public AuthResponse registerCustomer(CustomerRegisterRequest request) {
        log.info("[AuthService] Processing customer registration for email: {}", request.email());

        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER)
                .orElseThrow(() -> new ResourceNotFoundException("Role 'ROLE_CUSTOMER' not found"));

        User user = User.builder()
                .role(customerRole)
                .email(request.email().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .phoneNumber(request.phoneNumber().trim())
                .status(UserStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .build();

        User savedUser = userRepository.save(user);

        UserPrincipal principal = UserPrincipal.create(savedUser);
        String accessToken = jwtTokenProvider.generateAccessToken(principal);
        String refreshToken = refreshTokenService.createRefreshToken(savedUser);

        return AuthResponse.of(
                accessToken,
                refreshToken,
                jwtProperties.getAccessTokenExpirationMs(),
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getRole().getName().name(),
                savedUser.getFullName()
        );
    }

    @Override
    @Transactional
    public RegistrationResponse registerMerchant(MerchantRegisterRequest request) {
        log.info("[AuthService] Processing merchant registration for email: {}, business: {}",
                request.email(), request.businessName());

        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        if (merchantRepository.existsByRegistrationNumber(request.registrationNumber())) {
            throw new EmailAlreadyExistsException("Business registration number '" + request.registrationNumber() + "' is already registered.");
        }

        Role merchantRole = roleRepository.findByName(RoleName.ROLE_MERCHANT)
                .orElseThrow(() -> new ResourceNotFoundException("Role 'ROLE_MERCHANT' not found"));

        User user = User.builder()
                .role(merchantRole)
                .email(request.email().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .phoneNumber(request.phoneNumber().trim())
                .status(UserStatus.PENDING_APPROVAL)
                .authProvider(AuthProvider.LOCAL)
                .build();

        User savedUser = userRepository.save(user);

        // Generate HMAC webhook signing secret
        byte[] secretBytes = new byte[32];
        secureRandom.nextBytes(secretBytes);
        String webhookSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);

        String oauthClientId = "mer_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        Merchant merchant = Merchant.builder()
                .user(savedUser)
                .businessName(request.businessName().trim())
                .registrationNumber(request.registrationNumber().trim())
                .webhookUrl(request.webhookUrl() != null ? request.webhookUrl().trim() : null)
                .webhookSecret(webhookSecret)
                .oauthClientId(oauthClientId)
                .build();

        merchantRepository.save(merchant);

        return new RegistrationResponse(
                "Merchant account created successfully. Awaiting administrator review and approval.",
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getRole().getName(),
                savedUser.getStatus()
        );
    }

    @Override
    @Transactional
    public RegistrationResponse registerCourier(CourierRegisterRequest request) {
        log.info("[AuthService] Processing courier registration for email: {}, vehicle: {}",
                request.email(), request.vehicleType());

        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        if (courierRepository.existsByLicensePlate(request.licensePlate())) {
            throw new EmailAlreadyExistsException("Vehicle license plate '" + request.licensePlate() + "' is already registered.");
        }

        Role courierRole = roleRepository.findByName(RoleName.ROLE_COURIER)
                .orElseThrow(() -> new ResourceNotFoundException("Role 'ROLE_COURIER' not found"));

        User user = User.builder()
                .role(courierRole)
                .email(request.email().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .phoneNumber(request.phoneNumber().trim())
                .status(UserStatus.PENDING_APPROVAL)
                .authProvider(AuthProvider.LOCAL)
                .build();

        User savedUser = userRepository.save(user);

        Courier courier = Courier.builder()
                .user(savedUser)
                .vehicleType(request.vehicleType())
                .licensePlate(request.licensePlate().trim().toUpperCase())
                .assignedZoneId(request.assignedZoneId())
                .isAvailable(true)
                .activeDeliveryCount(0)
                .build();

        courierRepository.save(courier);

        return new RegistrationResponse(
                "Courier account created successfully. Awaiting administrator review and approval.",
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getRole().getName(),
                savedUser.getStatus()
        );
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("[AuthService] Attempting login for email: {}", request.email());

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email().toLowerCase().trim(),
                            request.password()
                    )
            );
        } catch (BadCredentialsException e) {
            log.warn("[AuthService] Authentication failed for email: {}", request.email());
            throw new BadCredentialsException("Invalid email or password.");
        }

        User user = userRepository.findByEmail(request.email().toLowerCase().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password."));

        if (user.getStatus() == UserStatus.PENDING_APPROVAL) {
            log.warn("[AuthService] Login denied - account pending approval for: {}", user.getEmail());
            throw new AccountPendingApprovalException(
                    "Your account is currently awaiting administrator review and approval."
            );
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            log.warn("[AuthService] Login denied - account suspended for: {}", user.getEmail());
            throw new AccountSuspendedException(
                    "Your account has been suspended by an administrator. Please contact support."
            );
        }

        if (user.getStatus() == UserStatus.REJECTED) {
            log.warn("[AuthService] Login denied - account application rejected for: {}", user.getEmail());
            String reason = user.getStatusReason() != null ? user.getStatusReason() : "Application rejected by administrator.";
            throw new AccountRejectedException("Your account application was rejected. Reason: " + reason);
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String accessToken = jwtTokenProvider.generateAccessToken(principal);
        String refreshToken = refreshTokenService.createRefreshToken(user);

        return AuthResponse.of(
                accessToken,
                refreshToken,
                jwtProperties.getAccessTokenExpirationMs(),
                user.getId(),
                user.getEmail(),
                user.getRole().getName().name(),
                user.getFullName()
        );
    }

    @Override
    @Transactional
    public AuthResponse authenticateGoogle(GoogleAuthRequest request) {
        log.info("[AuthService] Processing Google OAuth 2.0 authentication");

        GoogleIdToken googleIdToken;
        try {
            googleIdToken = googleIdTokenVerifier.verify(request.idToken());
        } catch (Exception e) {
            log.error("[AuthService] Exception while verifying Google ID token: {}", e.getMessage());
            throw new BadCredentialsException("Failed to verify Google ID token.");
        }

        if (googleIdToken == null) {
            log.warn("[AuthService] Google ID token is invalid or expired");
            throw new BadCredentialsException("Google ID token is invalid or expired.");
        }

        GoogleIdToken.Payload payload = googleIdToken.getPayload();
        Boolean emailVerified = payload.getEmailVerified();
        if (!Boolean.TRUE.equals(emailVerified)) {
            log.warn("[AuthService] Google ID token email is unverified for: {}", payload.getEmail());
            throw new BadCredentialsException("Google email address is not verified.");
        }

        String email = payload.getEmail().toLowerCase().trim();
        String googleSub = payload.getSubject();
        String name = (String) payload.get("name");
        if (name == null || name.trim().isEmpty()) {
            name = email.split("@")[0]; // Fallback for missing name claim
        }
        final String resolvedFullName = name.trim();

        SocialUserProfile profile = new SocialUserProfile(AuthProvider.GOOGLE, googleSub, email, resolvedFullName);
        return processSocialAuthentication(profile);
    }

    @Override
    @Transactional
    public AuthResponse authenticateApple(AppleAuthRequest request) {
        log.info("[AuthService] Processing Apple Sign In authentication");
        SocialUserProfile profile = appleIdTokenVerifier.verify(request.identityToken(), request.fullName());
        return processSocialAuthentication(profile);
    }

    @Override
    @Transactional
    public AuthResponse authenticateFacebook(FacebookAuthRequest request) {
        log.info("[AuthService] Processing Facebook Login authentication");
        SocialUserProfile profile = facebookClient.verify(request.accessToken());
        return processSocialAuthentication(profile);
    }

    /**
     * Unified 3-way user resolution, lifecycle state machine validation, and JWT token issuance
     * for all third-party identity providers (Google, Apple, Facebook).
     */
    private AuthResponse processSocialAuthentication(SocialUserProfile profile) {
        // 3-way User Resolution:
        // Scenario A: Existing user matched by authProvider & providerId
        User user = userRepository.findByAuthProviderAndProviderId(profile.provider(), profile.providerId())
                .orElseGet(() -> userRepository.findByEmail(profile.email())
                        // Scenario B: Existing user with matching email -> Link providerId while preserving password login!
                        // Multi-provider preservation: only link providerId if not already populated.
                        .map(existingUser -> {
                            if (existingUser.getProviderId() == null) {
                                existingUser.setProviderId(profile.providerId());
                                log.info("[AuthService] Linked {} providerId for existing user: {}", profile.provider(), profile.email());
                                return userRepository.save(existingUser);
                            }
                            log.info("[AuthService] Authenticated existing user {} via {} without overwriting primary link ({})",
                                    profile.email(), profile.provider(), existingUser.getAuthProvider());
                            return existingUser;
                        })
                        // Scenario C: Brand new user -> Auto-register as Customer
                        .orElseGet(() -> autoRegisterSocialCustomer(profile)));

        // State Machine validation (checks REJECTED alongside SUSPENDED and PENDING)
        if (user.getStatus() == UserStatus.PENDING_APPROVAL) {
            log.warn("[AuthService] {} login denied - account pending approval for: {}", profile.provider(), user.getEmail());
            throw new AccountPendingApprovalException("Your account is currently awaiting administrator review and approval.");
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            log.warn("[AuthService] {} login denied - account suspended for: {}", profile.provider(), user.getEmail());
            throw new AccountSuspendedException("Your account has been suspended by an administrator. Please contact support.");
        }

        if (user.getStatus() == UserStatus.REJECTED) {
            log.warn("[AuthService] {} login denied - account application rejected for: {}", profile.provider(), user.getEmail());
            String reason = user.getStatusReason() != null ? user.getStatusReason() : "Application rejected by administrator.";
            throw new AccountRejectedException("Your account application was rejected. Reason: " + reason);
        }

        UserPrincipal principal = UserPrincipal.create(user);
        String accessToken = jwtTokenProvider.generateAccessToken(principal);
        String refreshToken = refreshTokenService.createRefreshToken(user);

        log.info("[AuthService] Successfully authenticated {} user: {} with role: {}",
                profile.provider(), user.getEmail(), user.getRole().getName());

        return AuthResponse.of(
                accessToken,
                refreshToken,
                jwtProperties.getAccessTokenExpirationMs(),
                user.getId(),
                user.getEmail(),
                user.getRole().getName().name(),
                user.getFullName()
        );
    }

    private User autoRegisterSocialCustomer(SocialUserProfile profile) {
        log.info("[AuthService] Auto-registering new customer via {} OAuth: {}", profile.provider(), profile.email());

        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER)
                .orElseThrow(() -> new ResourceNotFoundException("Role 'ROLE_CUSTOMER' not found"));

        User newUser = User.builder()
                .role(customerRole)
                .email(profile.email())
                .fullName(profile.fullName())
                .phoneNumber(null) // Nullable for social auto-registered users
                .passwordHash(null) // Social login users do not have a local password
                .status(UserStatus.ACTIVE)
                .authProvider(profile.provider())
                .providerId(profile.providerId())
                .build();

        return userRepository.save(newUser);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshTokenService.RotatedToken rotated = refreshTokenService.verifyAndRotate(request.refreshToken());
        User user = rotated.user();

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountPendingApprovalException("User account is not currently active.");
        }

        UserPrincipal principal = UserPrincipal.create(user);
        String newAccessToken = jwtTokenProvider.generateAccessToken(principal);

        return AuthResponse.of(
                newAccessToken,
                rotated.newRefreshToken(),
                jwtProperties.getAccessTokenExpirationMs(),
                user.getId(),
                user.getEmail(),
                user.getRole().getName().name(),
                user.getFullName()
        );
    }

    @Override
    @Transactional
    public void logout(UUID currentUserId) {
        log.info("[AuthService] Revoking all refresh tokens on logout for userId: {}", currentUserId);
        refreshTokenService.revokeAllByUserId(currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(UUID currentUserId) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + currentUserId));

        return UserProfileResponse.fromEntity(user);
    }
}
