package com.nyxora.swiftdrop.auth.service.impl;

import com.nyxora.swiftdrop.auth.config.JwtProperties;
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
import com.nyxora.swiftdrop.auth.service.AuthService;
import com.nyxora.swiftdrop.auth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
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

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().toLowerCase().trim(), request.password())
        );

        User user = userRepository.findByEmail(request.email().toLowerCase().trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.email()));

        if (user.getStatus() == UserStatus.PENDING_APPROVAL) {
            log.warn("[AuthService] Login denied - account pending approval for: {}", user.getEmail());
            throw new AccountPendingApprovalException("Your account is currently awaiting administrator review and approval.");
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            log.warn("[AuthService] Login denied - account suspended for: {}", user.getEmail());
            throw new AccountSuspendedException("Your account has been suspended by an administrator. Please contact support.");
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

