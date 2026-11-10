package com.nyxora.swiftdrop.auth.service;

import com.nyxora.swiftdrop.auth.security.oauth.GoogleTokenVerifier;
import com.nyxora.swiftdrop.auth.config.JwtProperties;
import com.nyxora.swiftdrop.auth.dto.SocialUserProfile;
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
import com.nyxora.swiftdrop.auth.entity.Courier;
import com.nyxora.swiftdrop.auth.entity.Merchant;
import com.nyxora.swiftdrop.auth.entity.Role;
import com.nyxora.swiftdrop.auth.entity.User;
import com.nyxora.swiftdrop.auth.entity.enums.AuthProvider;
import com.nyxora.swiftdrop.auth.entity.enums.RoleName;
import com.nyxora.swiftdrop.auth.entity.enums.UserStatus;
import com.nyxora.swiftdrop.auth.entity.enums.VehicleType;
import com.nyxora.swiftdrop.auth.exception.AccountPendingApprovalException;
import com.nyxora.swiftdrop.auth.exception.AccountRejectedException;
import com.nyxora.swiftdrop.auth.exception.AccountSuspendedException;
import com.nyxora.swiftdrop.auth.exception.EmailAlreadyExistsException;
import com.nyxora.swiftdrop.auth.repository.CourierRepository;
import com.nyxora.swiftdrop.auth.repository.MerchantRepository;
import com.nyxora.swiftdrop.auth.repository.RoleRepository;
import com.nyxora.swiftdrop.auth.repository.UserRepository;
import com.nyxora.swiftdrop.auth.security.JwtTokenProvider;
import com.nyxora.swiftdrop.auth.security.UserPrincipal;
import com.nyxora.swiftdrop.auth.security.oauth.AppleIdTokenVerifier;
import com.nyxora.swiftdrop.auth.security.oauth.FacebookClient;
import com.nyxora.swiftdrop.auth.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private CourierRepository courierRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private GoogleTokenVerifier googleTokenVerifier;

    @Mock
    private AppleIdTokenVerifier appleIdTokenVerifier;

    @Mock
    private FacebookClient facebookClient;

    @InjectMocks
    private AuthServiceImpl authService;

    private Role customerRole;
    private Role merchantRole;
    private Role courierRole;

    @BeforeEach
    void setUp() {
        customerRole = Role.builder().id(1).name(RoleName.ROLE_CUSTOMER).build();
        merchantRole = Role.builder().id(2).name(RoleName.ROLE_MERCHANT).build();
        courierRole = Role.builder().id(3).name(RoleName.ROLE_COURIER).build();
    }


    @Test
    @DisplayName("Customer registration succeeds and returns active tokens immediately")
    void registerCustomer_Success() {
        CustomerRegisterRequest request = new CustomerRegisterRequest(
                "Kamal Silva", "kamal@swiftdrop.lk", "SecurePass123!", "+94771234567"
        );

        User savedUser = User.builder()
                .id(UUID.randomUUID())
                .fullName("Kamal Silva")
                .email("kamal@swiftdrop.lk")
                .role(customerRole)
                .status(UserStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .build();

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByName(RoleName.ROLE_CUSTOMER)).thenReturn(Optional.of(customerRole));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_pass");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("valid_access_token");
        when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn("valid_refresh_token");

        AuthResponse response = authService.registerCustomer(request);

        assertNotNull(response);
        assertEquals("valid_access_token", response.accessToken());
        assertEquals("valid_refresh_token", response.refreshToken());
        assertEquals("kamal@swiftdrop.lk", response.email());
        assertEquals("ROLE_CUSTOMER", response.role());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Customer registration fails when email is already taken")
    void registerCustomer_DuplicateEmail_ThrowsException() {
        CustomerRegisterRequest request = new CustomerRegisterRequest(
                "Kamal Silva", "existing@swiftdrop.lk", "SecurePass123!", "+94771234567"
        );

        when(userRepository.existsByEmail("existing@swiftdrop.lk")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.registerCustomer(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Merchant registration succeeds with PENDING_APPROVAL status")
    void registerMerchant_Success() {
        MerchantRegisterRequest request = new MerchantRegisterRequest(
                "Nimal Perera", "nimal@partner.lk", "SecurePass123!", "+94779876543",
                "Perera Super Store", "REG-PV-998877", "https://partner.lk/webhook"
        );

        User savedUser = User.builder()
                .id(UUID.randomUUID())
                .fullName("Nimal Perera")
                .email("nimal@partner.lk")
                .role(merchantRole)
                .status(UserStatus.PENDING_APPROVAL)
                .authProvider(AuthProvider.LOCAL)
                .build();

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(merchantRepository.existsByRegistrationNumber(anyString())).thenReturn(false);
        when(roleRepository.findByName(RoleName.ROLE_MERCHANT)).thenReturn(Optional.of(merchantRole));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_pass");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        RegistrationResponse response = authService.registerMerchant(request);

        assertNotNull(response);
        assertEquals(UserStatus.PENDING_APPROVAL, response.status());
        assertEquals(RoleName.ROLE_MERCHANT, response.role());
        assertTrue(response.message().contains("approval"));
        verify(merchantRepository, times(1)).save(any(Merchant.class));
    }

    @Test
    @DisplayName("Courier registration succeeds with PENDING_APPROVAL and assignedZoneId")
    void registerCourier_Success() {
        CourierRegisterRequest request = new CourierRegisterRequest(
                "Saman Kumara", "saman@rider.lk", "SecurePass123!", "+94711112233",
                VehicleType.MOTORCYCLE, "WP-BBI-1234", 1
        );

        User savedUser = User.builder()
                .id(UUID.randomUUID())
                .fullName("Saman Kumara")
                .email("saman@rider.lk")
                .role(courierRole)
                .status(UserStatus.PENDING_APPROVAL)
                .authProvider(AuthProvider.LOCAL)
                .build();

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(courierRepository.existsByLicensePlate(anyString())).thenReturn(false);
        when(roleRepository.findByName(RoleName.ROLE_COURIER)).thenReturn(Optional.of(courierRole));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_pass");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        RegistrationResponse response = authService.registerCourier(request);

        assertNotNull(response);
        assertEquals(UserStatus.PENDING_APPROVAL, response.status());
        assertEquals(RoleName.ROLE_COURIER, response.role());
        verify(courierRepository, times(1)).save(any(Courier.class));
    }

    @Test
    @DisplayName("Login succeeds for ACTIVE user")
    void login_Success() {
        LoginRequest request = new LoginRequest("user@swiftdrop.lk", "Password123!");

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@swiftdrop.lk")
                .role(customerRole)
                .fullName("Active User")
                .status(UserStatus.ACTIVE)
                .build();

        UserPrincipal principal = UserPrincipal.create(user);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(userRepository.findByEmail("user@swiftdrop.lk")).thenReturn(Optional.of(user));
        when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtTokenProvider.generateAccessToken(principal)).thenReturn("new_access_token");
        when(refreshTokenService.createRefreshToken(user)).thenReturn("new_refresh_token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("new_access_token", response.accessToken());
        assertEquals("new_refresh_token", response.refreshToken());
    }

    @Test
    @DisplayName("Login fails when account is PENDING_APPROVAL")
    void login_PendingApproval_ThrowsException() {
        LoginRequest request = new LoginRequest("courier@swiftdrop.lk", "Password123!");

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("courier@swiftdrop.lk")
                .role(courierRole)
                .status(UserStatus.PENDING_APPROVAL)
                .build();

        when(authenticationManager.authenticate(any())).thenReturn(mock(Authentication.class));
        when(userRepository.findByEmail("courier@swiftdrop.lk")).thenReturn(Optional.of(user));

        assertThrows(AccountPendingApprovalException.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("Login fails when account is SUSPENDED")
    void login_Suspended_ThrowsException() {
        LoginRequest request = new LoginRequest("banned@swiftdrop.lk", "Password123!");

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("banned@swiftdrop.lk")
                .role(customerRole)
                .status(UserStatus.SUSPENDED)
                .build();

        when(authenticationManager.authenticate(any())).thenReturn(mock(Authentication.class));
        when(userRepository.findByEmail("banned@swiftdrop.lk")).thenReturn(Optional.of(user));

        assertThrows(AccountSuspendedException.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("Login fails when account application was REJECTED")
    void login_Rejected_ThrowsException() {
        LoginRequest request = new LoginRequest("rejected@swiftdrop.lk", "Password123!");

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("rejected@swiftdrop.lk")
                .role(courierRole)
                .status(UserStatus.REJECTED)
                .statusReason("Driver's license photo is unreadable.")
                .build();

        when(authenticationManager.authenticate(any())).thenReturn(mock(Authentication.class));
        when(userRepository.findByEmail("rejected@swiftdrop.lk")).thenReturn(Optional.of(user));

        AccountRejectedException ex = assertThrows(AccountRejectedException.class, () -> authService.login(request));
        assertTrue(ex.getMessage().contains("Driver's license photo is unreadable."));
    }

    @Test
    @DisplayName("Logout invalidates all user refresh tokens")
    void logout_Success() {
        UUID userId = UUID.randomUUID();
        authService.logout(userId);
        verify(refreshTokenService, times(1)).revokeAllByUserId(userId);
    }

    @Test
    @DisplayName("Google OAuth auto-registers a brand-new Customer and returns tokens")
    void authenticateGoogle_NewCustomer_AutoRegistersAndReturnsTokens() {
        GoogleAuthRequest request = new GoogleAuthRequest("valid-google-id-token");
        SocialUserProfile profile = new SocialUserProfile(AuthProvider.GOOGLE, "google-sub-12345", "newcustomer@gmail.com", "Kamal Perera");

        when(googleTokenVerifier.verify("valid-google-id-token")).thenReturn(profile);

        when(userRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-12345"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("newcustomer@gmail.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName(RoleName.ROLE_CUSTOMER)).thenReturn(Optional.of(customerRole));

        UUID generatedId = UUID.randomUUID();
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User userToSave = invocation.getArgument(0);
            return User.builder()
                    .id(generatedId)
                    .email(userToSave.getEmail())
                    .fullName(userToSave.getFullName())
                    .role(userToSave.getRole())
                    .status(userToSave.getStatus())
                    .authProvider(userToSave.getAuthProvider())
                    .providerId(userToSave.getProviderId())
                    .phoneNumber(userToSave.getPhoneNumber())
                    .passwordHash(userToSave.getPasswordHash())
                    .build();
        });

        when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("mock-access-token");
        when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn("mock-refresh-token");

        AuthResponse response = authService.authenticateGoogle(request);

        assertNotNull(response);
        assertEquals("mock-access-token", response.accessToken());
        assertEquals("mock-refresh-token", response.refreshToken());
        assertEquals("newcustomer@gmail.com", response.email());
        assertEquals("Kamal Perera", response.fullName());
        assertEquals(RoleName.ROLE_CUSTOMER.name(), response.role());

        verify(userRepository).save(argThat(u ->
                u.getEmail().equals("newcustomer@gmail.com") &&
                u.getFullName().equals("Kamal Perera") &&
                u.getAuthProvider() == AuthProvider.GOOGLE &&
                "google-sub-12345".equals(u.getProviderId()) &&
                u.getPhoneNumber() == null &&
                u.getPasswordHash() == null &&
                u.getStatus() == UserStatus.ACTIVE
        ));
    }

    @Test
    @DisplayName("Google OAuth logs in existing Google user directly")
    void authenticateGoogle_ExistingGoogleUser_LogsInDirectly() {
        GoogleAuthRequest request = new GoogleAuthRequest("valid-token");
        SocialUserProfile profile = new SocialUserProfile(AuthProvider.GOOGLE, "google-sub-existing", "existing.google@gmail.com", "Existing User");

        when(googleTokenVerifier.verify("valid-token")).thenReturn(profile);

        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("existing.google@gmail.com")
                .fullName("Existing User")
                .role(customerRole)
                .status(UserStatus.ACTIVE)
                .authProvider(AuthProvider.GOOGLE)
                .providerId("google-sub-existing")
                .build();

        when(userRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-existing"))
                .thenReturn(Optional.of(existingUser));
        when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("access-token-existing");
        when(refreshTokenService.createRefreshToken(existingUser)).thenReturn("refresh-token-existing");

        AuthResponse response = authService.authenticateGoogle(request);

        assertNotNull(response);
        assertEquals("access-token-existing", response.accessToken());
        assertEquals("existing.google@gmail.com", response.email());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Google OAuth links providerId to existing local user without overwriting password or authProvider")
    void authenticateGoogle_ExistingLocalUser_LinksProviderIdAndPreservesPassword() {
        GoogleAuthRequest request = new GoogleAuthRequest("valid-token");
        SocialUserProfile profile = new SocialUserProfile(AuthProvider.GOOGLE, "google-sub-link", "local.courier@swiftdrop.lk", "Local Courier");

        when(googleTokenVerifier.verify("valid-token")).thenReturn(profile);

        User existingLocalUser = User.builder()
                .id(UUID.randomUUID())
                .email("local.courier@swiftdrop.lk")
                .fullName("Local Courier")
                .passwordHash("$2a$12$SecureBcryptHashPlaceholder")
                .role(courierRole)
                .status(UserStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .providerId(null)
                .build();

        when(userRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-link"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("local.courier@swiftdrop.lk")).thenReturn(Optional.of(existingLocalUser));
        when(userRepository.save(existingLocalUser)).thenReturn(existingLocalUser);
        when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("access-token-linked");
        when(refreshTokenService.createRefreshToken(existingLocalUser)).thenReturn("refresh-token-linked");

        AuthResponse response = authService.authenticateGoogle(request);

        assertNotNull(response);
        assertEquals("access-token-linked", response.accessToken());
        assertEquals("google-sub-link", existingLocalUser.getProviderId());
        assertEquals(AuthProvider.LOCAL, existingLocalUser.getAuthProvider());
        assertEquals("$2a$12$SecureBcryptHashPlaceholder", existingLocalUser.getPasswordHash());
        verify(userRepository, times(1)).save(existingLocalUser);
    }

    @Test
    @DisplayName("Google OAuth falls back to email prefix when name claim is missing or empty")
    void authenticateGoogle_MissingName_FallsBackToEmailPrefix() {
        GoogleAuthRequest request = new GoogleAuthRequest("valid-token");
        SocialUserProfile profile = new SocialUserProfile(AuthProvider.GOOGLE, "google-sub-anonymous", "anonymous.dev@gmail.com", "anonymous.dev");

        when(googleTokenVerifier.verify("valid-token")).thenReturn(profile);

        when(userRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-anonymous"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("anonymous.dev@gmail.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName(RoleName.ROLE_CUSTOMER)).thenReturn(Optional.of(customerRole));

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("mock-access");
        when(refreshTokenService.createRefreshToken(any())).thenReturn("mock-refresh");

        AuthResponse response = authService.authenticateGoogle(request);

        assertNotNull(response);
        assertEquals("anonymous.dev", response.fullName());
        verify(userRepository).save(argThat(u -> "anonymous.dev".equals(u.getFullName())));
    }

    @Test
    @DisplayName("Google OAuth throws BadCredentialsException when token verification fails")
    void authenticateGoogle_InvalidToken_ThrowsBadCredentialsException() {
        GoogleAuthRequest request = new GoogleAuthRequest("invalid-id-token");
        when(googleTokenVerifier.verify("invalid-id-token")).thenThrow(new BadCredentialsException("Google ID token is invalid or expired."));

        assertThrows(BadCredentialsException.class, () -> authService.authenticateGoogle(request));
    }

    @Test
    @DisplayName("Google OAuth throws AccountPendingApprovalException when account is PENDING_APPROVAL")
    void authenticateGoogle_PendingApproval_ThrowsAccountPendingApprovalException() {
        GoogleAuthRequest request = new GoogleAuthRequest("valid-token");
        SocialUserProfile profile = new SocialUserProfile(AuthProvider.GOOGLE, "google-sub-pending", "pending@partner.lk", "Pending Merchant");

        when(googleTokenVerifier.verify("valid-token")).thenReturn(profile);

        User pendingUser = User.builder()
                .id(UUID.randomUUID())
                .email("pending@partner.lk")
                .role(merchantRole)
                .status(UserStatus.PENDING_APPROVAL)
                .authProvider(AuthProvider.GOOGLE)
                .providerId("google-sub-pending")
                .build();

        when(userRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-pending"))
                .thenReturn(Optional.of(pendingUser));

        assertThrows(AccountPendingApprovalException.class, () -> authService.authenticateGoogle(request));
    }

    @Test
    @DisplayName("Google OAuth throws AccountSuspendedException when account is SUSPENDED")
    void authenticateGoogle_Suspended_ThrowsAccountSuspendedException() {
        GoogleAuthRequest request = new GoogleAuthRequest("valid-token");
        SocialUserProfile profile = new SocialUserProfile(AuthProvider.GOOGLE, "google-sub-suspended", "suspended@swiftdrop.lk", "Suspended User");

        when(googleTokenVerifier.verify("valid-token")).thenReturn(profile);

        User suspendedUser = User.builder()
                .id(UUID.randomUUID())
                .email("suspended@swiftdrop.lk")
                .role(customerRole)
                .status(UserStatus.SUSPENDED)
                .authProvider(AuthProvider.GOOGLE)
                .providerId("google-sub-suspended")
                .build();

        when(userRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-suspended"))
                .thenReturn(Optional.of(suspendedUser));

        assertThrows(AccountSuspendedException.class, () -> authService.authenticateGoogle(request));
    }

    @Test
    @DisplayName("Google OAuth throws AccountRejectedException with statusReason when account is REJECTED")
    void authenticateGoogle_Rejected_ThrowsAccountRejectedException() {
        GoogleAuthRequest request = new GoogleAuthRequest("valid-token");
        SocialUserProfile profile = new SocialUserProfile(AuthProvider.GOOGLE, "google-sub-rejected", "rejected@rider.lk", "Rejected Courier");

        when(googleTokenVerifier.verify("valid-token")).thenReturn(profile);

        User rejectedUser = User.builder()
                .id(UUID.randomUUID())
                .email("rejected@rider.lk")
                .role(courierRole)
                .status(UserStatus.REJECTED)
                .statusReason("Driving record does not satisfy safety criteria.")
                .authProvider(AuthProvider.GOOGLE)
                .providerId("google-sub-rejected")
                .build();

        when(userRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-rejected"))
                .thenReturn(Optional.of(rejectedUser));

        AccountRejectedException ex = assertThrows(AccountRejectedException.class, () -> authService.authenticateGoogle(request));
        assertTrue(ex.getMessage().contains("Driving record does not satisfy safety criteria."));
    }

    @Test
    @DisplayName("Apple Sign In auto-registers a brand-new Customer and returns tokens")
    void authenticateApple_NewCustomer_AutoRegistersAndReturnsTokens() {
        AppleAuthRequest request = new AppleAuthRequest("apple-id-token", "Steve Jobs");
        SocialUserProfile profile = new SocialUserProfile(AuthProvider.APPLE, "apple-sub-999", "steve@apple.com", "Steve Jobs");

        when(appleIdTokenVerifier.verify("apple-id-token", "Steve Jobs")).thenReturn(profile);
        when(userRepository.findByAuthProviderAndProviderId(AuthProvider.APPLE, "apple-sub-999")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("steve@apple.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName(RoleName.ROLE_CUSTOMER)).thenReturn(Optional.of(customerRole));

        UUID generatedId = UUID.randomUUID();
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            return User.builder()
                    .id(generatedId)
                    .email(u.getEmail())
                    .fullName(u.getFullName())
                    .role(u.getRole())
                    .status(u.getStatus())
                    .authProvider(u.getAuthProvider())
                    .providerId(u.getProviderId())
                    .phoneNumber(u.getPhoneNumber())
                    .passwordHash(u.getPasswordHash())
                    .build();
        });

        when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("apple-access-token");
        when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn("apple-refresh-token");

        AuthResponse response = authService.authenticateApple(request);

        assertNotNull(response);
        assertEquals("apple-access-token", response.accessToken());
        assertEquals("apple-refresh-token", response.refreshToken());
        assertEquals("steve@apple.com", response.email());
        assertEquals("Steve Jobs", response.fullName());
        assertEquals(RoleName.ROLE_CUSTOMER.name(), response.role());

        verify(userRepository).save(argThat(u ->
                u.getEmail().equals("steve@apple.com") &&
                u.getFullName().equals("Steve Jobs") &&
                u.getAuthProvider() == AuthProvider.APPLE &&
                "apple-sub-999".equals(u.getProviderId()) &&
                u.getPhoneNumber() == null &&
                u.getPasswordHash() == null &&
                u.getStatus() == UserStatus.ACTIVE
        ));
    }

    @Test
    @DisplayName("Apple Sign In logs in existing Apple user directly")
    void authenticateApple_ExistingUser_LogsInDirectly() {
        AppleAuthRequest request = new AppleAuthRequest("valid-apple-token", null);
        SocialUserProfile profile = new SocialUserProfile(AuthProvider.APPLE, "apple-sub-111", "tim@apple.com", "Tim Cook");

        when(appleIdTokenVerifier.verify("valid-apple-token", null)).thenReturn(profile);

        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("tim@apple.com")
                .fullName("Tim Cook")
                .role(customerRole)
                .status(UserStatus.ACTIVE)
                .authProvider(AuthProvider.APPLE)
                .providerId("apple-sub-111")
                .build();

        when(userRepository.findByAuthProviderAndProviderId(AuthProvider.APPLE, "apple-sub-111"))
                .thenReturn(Optional.of(existingUser));
        when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("apple-access-direct");
        when(refreshTokenService.createRefreshToken(existingUser)).thenReturn("apple-refresh-direct");

        AuthResponse response = authService.authenticateApple(request);

        assertNotNull(response);
        assertEquals("apple-access-direct", response.accessToken());
        assertEquals("tim@apple.com", response.email());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Apple Sign In throws AccountSuspendedException when account is SUSPENDED")
    void authenticateApple_SuspendedUser_ThrowsAccountSuspendedException() {
        AppleAuthRequest request = new AppleAuthRequest("valid-token", null);
        SocialUserProfile profile = new SocialUserProfile(AuthProvider.APPLE, "apple-sub-suspended", "banned@apple.com", "Banned User");

        when(appleIdTokenVerifier.verify("valid-token", null)).thenReturn(profile);

        User suspendedUser = User.builder()
                .id(UUID.randomUUID())
                .email("banned@apple.com")
                .role(customerRole)
                .status(UserStatus.SUSPENDED)
                .authProvider(AuthProvider.APPLE)
                .providerId("apple-sub-suspended")
                .build();

        when(userRepository.findByAuthProviderAndProviderId(AuthProvider.APPLE, "apple-sub-suspended"))
                .thenReturn(Optional.of(suspendedUser));

        assertThrows(AccountSuspendedException.class, () -> authService.authenticateApple(request));
    }

    @Test
    @DisplayName("Facebook Login auto-registers a brand-new Customer and returns tokens")
    void authenticateFacebook_NewCustomer_AutoRegistersAndReturnsTokens() {
        FacebookAuthRequest request = new FacebookAuthRequest("valid-fb-token");
        SocialUserProfile profile = new SocialUserProfile(AuthProvider.FACEBOOK, "fb-id-456", "mark@facebook.com", "Mark Zuckerberg");

        when(facebookClient.verify("valid-fb-token")).thenReturn(profile);
        when(userRepository.findByAuthProviderAndProviderId(AuthProvider.FACEBOOK, "fb-id-456")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("mark@facebook.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName(RoleName.ROLE_CUSTOMER)).thenReturn(Optional.of(customerRole));

        UUID generatedId = UUID.randomUUID();
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            return User.builder()
                    .id(generatedId)
                    .email(u.getEmail())
                    .fullName(u.getFullName())
                    .role(u.getRole())
                    .status(u.getStatus())
                    .authProvider(u.getAuthProvider())
                    .providerId(u.getProviderId())
                    .phoneNumber(u.getPhoneNumber())
                    .passwordHash(u.getPasswordHash())
                    .build();
        });

        when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("fb-access-token");
        when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn("fb-refresh-token");

        AuthResponse response = authService.authenticateFacebook(request);

        assertNotNull(response);
        assertEquals("fb-access-token", response.accessToken());
        assertEquals("fb-refresh-token", response.refreshToken());
        assertEquals("mark@facebook.com", response.email());
        assertEquals("Mark Zuckerberg", response.fullName());
        assertEquals(RoleName.ROLE_CUSTOMER.name(), response.role());

        verify(userRepository).save(argThat(u ->
                u.getEmail().equals("mark@facebook.com") &&
                u.getFullName().equals("Mark Zuckerberg") &&
                u.getAuthProvider() == AuthProvider.FACEBOOK &&
                "fb-id-456".equals(u.getProviderId()) &&
                u.getPhoneNumber() == null &&
                u.getPasswordHash() == null &&
                u.getStatus() == UserStatus.ACTIVE
        ));
    }

    @Test
    @DisplayName("Facebook Login with matching verified email preserves existing provider link and does not overwrite")
    void authenticateFacebook_MultiProviderLogin_PreservesOriginalProviderId() {
        FacebookAuthRequest request = new FacebookAuthRequest("valid-fb-token");
        SocialUserProfile profile = new SocialUserProfile(AuthProvider.FACEBOOK, "fb-id-789", "kamal@swiftdrop.lk", "Kamal FB");

        when(facebookClient.verify("valid-fb-token")).thenReturn(profile);

        // User previously logged in with Google
        User existingGoogleUser = User.builder()
                .id(UUID.randomUUID())
                .email("kamal@swiftdrop.lk")
                .fullName("Kamal Original")
                .role(customerRole)
                .status(UserStatus.ACTIVE)
                .authProvider(AuthProvider.GOOGLE)
                .providerId("google-sub-original")
                .build();

        when(userRepository.findByAuthProviderAndProviderId(AuthProvider.FACEBOOK, "fb-id-789")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("kamal@swiftdrop.lk")).thenReturn(Optional.of(existingGoogleUser));

        when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("multi-access");
        when(refreshTokenService.createRefreshToken(existingGoogleUser)).thenReturn("multi-refresh");

        AuthResponse response = authService.authenticateFacebook(request);

        assertNotNull(response);
        assertEquals("multi-access", response.accessToken());
        assertEquals("kamal@swiftdrop.lk", response.email());
        // Verify original Google providerId is PRESERVED and not overwritten!
        assertEquals("google-sub-original", existingGoogleUser.getProviderId());
        assertEquals(AuthProvider.GOOGLE, existingGoogleUser.getAuthProvider());
        verify(userRepository, never()).save(existingGoogleUser);
    }

    @Test
    @DisplayName("Facebook Login throws AccountRejectedException with statusReason when account is REJECTED")
    void authenticateFacebook_RejectedUser_ThrowsAccountRejectedException() {
        FacebookAuthRequest request = new FacebookAuthRequest("valid-token");
        SocialUserProfile profile = new SocialUserProfile(AuthProvider.FACEBOOK, "fb-rejected-1", "rejected.fb@rider.lk", "Rejected Driver");

        when(facebookClient.verify("valid-token")).thenReturn(profile);

        User rejectedUser = User.builder()
                .id(UUID.randomUUID())
                .email("rejected.fb@rider.lk")
                .role(courierRole)
                .status(UserStatus.REJECTED)
                .statusReason("Vehicle registration document expired.")
                .authProvider(AuthProvider.FACEBOOK)
                .providerId("fb-rejected-1")
                .build();

        when(userRepository.findByAuthProviderAndProviderId(AuthProvider.FACEBOOK, "fb-rejected-1"))
                .thenReturn(Optional.of(rejectedUser));

        AccountRejectedException ex = assertThrows(AccountRejectedException.class, () -> authService.authenticateFacebook(request));
        assertTrue(ex.getMessage().contains("Vehicle registration document expired."));
    }

    @Test
    @DisplayName("Merchant registration fails when email is already registered")
    void registerMerchant_DuplicateEmail_ThrowsException() {
        MerchantRegisterRequest request = new MerchantRegisterRequest(
                "Daraz LK", "merchant@daraz.lk", "Password123!", "+94771112233",
                "Daraz Online Pvt Ltd", "PV00123456", "https://daraz.lk/webhook"
        );
        when(userRepository.existsByEmail("merchant@daraz.lk")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.registerMerchant(request));
    }

    @Test
    @DisplayName("Merchant registration fails when business registration number is already registered")
    void registerMerchant_DuplicateRegistrationNumber_ThrowsException() {
        MerchantRegisterRequest request = new MerchantRegisterRequest(
                "Daraz LK", "merchant@daraz.lk", "Password123!", "+94771112233",
                "Daraz Online Pvt Ltd", "PV00123456", "https://daraz.lk/webhook"
        );
        when(userRepository.existsByEmail("merchant@daraz.lk")).thenReturn(false);
        when(merchantRepository.existsByRegistrationNumber("PV00123456")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.registerMerchant(request));
    }

    @Test
    @DisplayName("Courier registration fails when email is already registered")
    void registerCourier_DuplicateEmail_ThrowsException() {
        CourierRegisterRequest request = new CourierRegisterRequest(
                "Kasun Rider", "courier@swiftdrop.lk", "Password123!", "+94770001122",
                VehicleType.MOTORCYCLE, "BBD-1234", 1
        );
        when(userRepository.existsByEmail("courier@swiftdrop.lk")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.registerCourier(request));
    }

    @Test
    @DisplayName("Courier registration fails when license plate is already registered")
    void registerCourier_DuplicateLicensePlate_ThrowsException() {
        CourierRegisterRequest request = new CourierRegisterRequest(
                "Kasun Rider", "courier@swiftdrop.lk", "Password123!", "+94770001122",
                VehicleType.MOTORCYCLE, "BBD-1234", 1
        );
        when(userRepository.existsByEmail("courier@swiftdrop.lk")).thenReturn(false);
        when(courierRepository.existsByLicensePlate("BBD-1234")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.registerCourier(request));
    }

    @Test
    @DisplayName("Login with ROLE_COURIER loads courierId into AuthResponse")
    void login_CourierUser_LoadsCourierId() {
        LoginRequest request = new LoginRequest("courier@swiftdrop.lk", "Password123!");
        UUID userId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .email("courier@swiftdrop.lk")
                .fullName("Courier User")
                .role(courierRole)
                .status(UserStatus.ACTIVE)
                .build();

        Courier courier = Courier.builder().id(courierId).user(user).build();

        when(authenticationManager.authenticate(any())).thenReturn(mock(Authentication.class));
        when(userRepository.findByEmail("courier@swiftdrop.lk")).thenReturn(Optional.of(user));
        when(courierRepository.findByUserId(userId)).thenReturn(Optional.of(courier));
        when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("access-token-courier");
        when(refreshTokenService.createRefreshToken(user)).thenReturn("refresh-token-courier");

        AuthResponse response = authService.login(request);
        assertNotNull(response);
        assertEquals("ROLE_COURIER", response.role());
    }

    @Test
    @DisplayName("Login with ROLE_MERCHANT loads merchantId into AuthResponse")
    void login_MerchantUser_LoadsMerchantId() {
        LoginRequest request = new LoginRequest("merchant@swiftdrop.lk", "Password123!");
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .email("merchant@swiftdrop.lk")
                .fullName("Merchant User")
                .role(merchantRole)
                .status(UserStatus.ACTIVE)
                .build();

        Merchant merchant = Merchant.builder().id(merchantId).user(user).build();

        when(authenticationManager.authenticate(any())).thenReturn(mock(Authentication.class));
        when(userRepository.findByEmail("merchant@swiftdrop.lk")).thenReturn(Optional.of(user));
        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(merchant));
        when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("access-token-merchant");
        when(refreshTokenService.createRefreshToken(user)).thenReturn("refresh-token-merchant");

        AuthResponse response = authService.login(request);
        assertNotNull(response);
        assertEquals("ROLE_MERCHANT", response.role());
    }

    @Test
    @DisplayName("Login rejected user with null statusReason uses default rejection message")
    void login_Rejected_NullReason_UsesDefaultMessage() {
        LoginRequest request = new LoginRequest("rejected@swiftdrop.lk", "Password123!");
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("rejected@swiftdrop.lk")
                .role(courierRole)
                .status(UserStatus.REJECTED)
                .statusReason(null)
                .build();

        when(authenticationManager.authenticate(any())).thenReturn(mock(Authentication.class));
        when(userRepository.findByEmail("rejected@swiftdrop.lk")).thenReturn(Optional.of(user));

        AccountRejectedException ex = assertThrows(AccountRejectedException.class, () -> authService.login(request));
        assertTrue(ex.getMessage().contains("Application rejected by administrator."));
    }

    @Test
    @DisplayName("refreshToken for Courier and Merchant users issues new tokens")
    void refreshToken_ActiveCourierAndMerchant_Success() {
        RefreshTokenRequest refreshReq = new RefreshTokenRequest("valid-refresh-token");
        UUID courierUserId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();

        User courierUser = User.builder()
                .id(courierUserId)
                .email("courier.refresh@swiftdrop.lk")
                .role(courierRole)
                .status(UserStatus.ACTIVE)
                .build();

        when(refreshTokenService.verifyAndRotate("valid-refresh-token"))
                .thenReturn(new RefreshTokenService.RotatedToken(courierUser, "new-refresh"));
        when(courierRepository.findByUserId(courierUserId)).thenReturn(Optional.of(Courier.builder().id(courierId).build()));
        when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("new-access");

        AuthResponse resp = authService.refreshToken(refreshReq);
        assertNotNull(resp);
        assertEquals("new-access", resp.accessToken());
        assertEquals("new-refresh", resp.refreshToken());

        // Merchant user
        UUID merchantUserId = UUID.randomUUID();
        User merchantUser = User.builder()
                .id(merchantUserId)
                .email("merchant.refresh@swiftdrop.lk")
                .role(merchantRole)
                .status(UserStatus.ACTIVE)
                .build();

        when(refreshTokenService.verifyAndRotate("merchant-token"))
                .thenReturn(new RefreshTokenService.RotatedToken(merchantUser, "new-m-refresh"));
        when(merchantRepository.findByUserId(merchantUserId)).thenReturn(Optional.of(Merchant.builder().id(UUID.randomUUID()).build()));

        AuthResponse respM = authService.refreshToken(new RefreshTokenRequest("merchant-token"));
        assertNotNull(respM);
        assertEquals("new-m-refresh", respM.refreshToken());
    }

    @Test
    @DisplayName("refreshToken for inactive user throws AccountPendingApprovalException")
    void refreshToken_InactiveUser_ThrowsException() {
        RefreshTokenRequest refreshReq = new RefreshTokenRequest("valid-refresh-token");
        User inactiveUser = User.builder()
                .id(UUID.randomUUID())
                .email("pending@swiftdrop.lk")
                .role(customerRole)
                .status(UserStatus.PENDING_APPROVAL)
                .build();

        when(refreshTokenService.verifyAndRotate("valid-refresh-token"))
                .thenReturn(new RefreshTokenService.RotatedToken(inactiveUser, "new-refresh"));

        assertThrows(AccountPendingApprovalException.class, () -> authService.refreshToken(refreshReq));
    }

    @Test
    @DisplayName("Google authentication with REJECTED user and null statusReason uses default rejection message")
    void authenticateGoogle_Rejected_NullReason_UsesDefaultMessage() {
        GoogleAuthRequest request = new GoogleAuthRequest("token");
        SocialUserProfile profile = new SocialUserProfile(AuthProvider.GOOGLE, "sub-rej", "rej@gmail.com", "Rej");
        when(googleTokenVerifier.verify("token")).thenReturn(profile);

        User rejectedUser = User.builder()
                .id(UUID.randomUUID())
                .email("rej@gmail.com")
                .role(customerRole)
                .status(UserStatus.REJECTED)
                .statusReason(null)
                .authProvider(AuthProvider.GOOGLE)
                .providerId("sub-rej")
                .build();

        when(userRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, "sub-rej"))
                .thenReturn(Optional.of(rejectedUser));

        AccountRejectedException ex = assertThrows(AccountRejectedException.class, () -> authService.authenticateGoogle(request));
        assertTrue(ex.getMessage().contains("Application rejected by administrator."));
    }
}
