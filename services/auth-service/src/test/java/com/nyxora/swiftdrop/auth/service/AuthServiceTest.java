package com.nyxora.swiftdrop.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.nyxora.swiftdrop.auth.config.JwtProperties;
import com.nyxora.swiftdrop.auth.dto.request.CourierRegisterRequest;
import com.nyxora.swiftdrop.auth.dto.request.CustomerRegisterRequest;
import com.nyxora.swiftdrop.auth.dto.request.GoogleAuthRequest;
import com.nyxora.swiftdrop.auth.dto.request.LoginRequest;
import com.nyxora.swiftdrop.auth.dto.request.MerchantRegisterRequest;
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
    private GoogleIdTokenVerifier googleIdTokenVerifier;

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

    private GoogleIdToken createMockGoogleIdToken(String email, String sub, String name, Boolean emailVerified) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail(email);
        payload.setSubject(sub);
        payload.setEmailVerified(emailVerified);
        if (name != null) {
            payload.set("name", name);
        }

        GoogleIdToken idToken = mock(GoogleIdToken.class);
        when(idToken.getPayload()).thenReturn(payload);
        return idToken;
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
    void authenticateGoogle_NewCustomer_AutoRegistersAndReturnsTokens() throws Exception {
        GoogleAuthRequest request = new GoogleAuthRequest("valid-google-id-token");
        GoogleIdToken googleIdToken = createMockGoogleIdToken("newcustomer@gmail.com", "google-sub-12345", "Kamal Perera", true);

        when(googleIdTokenVerifier.verify("valid-google-id-token")).thenReturn(googleIdToken);

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
    void authenticateGoogle_ExistingGoogleUser_LogsInDirectly() throws Exception {
        GoogleAuthRequest request = new GoogleAuthRequest("valid-token");
        GoogleIdToken googleIdToken = createMockGoogleIdToken("existing.google@gmail.com", "google-sub-existing", "Existing User", true);

        when(googleIdTokenVerifier.verify("valid-token")).thenReturn(googleIdToken);

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
    void authenticateGoogle_ExistingLocalUser_LinksProviderIdAndPreservesPassword() throws Exception {
        GoogleAuthRequest request = new GoogleAuthRequest("valid-token");
        GoogleIdToken googleIdToken = createMockGoogleIdToken("local.courier@swiftdrop.lk", "google-sub-link", "Local Courier", true);

        when(googleIdTokenVerifier.verify("valid-token")).thenReturn(googleIdToken);

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
    void authenticateGoogle_MissingName_FallsBackToEmailPrefix() throws Exception {
        GoogleAuthRequest request = new GoogleAuthRequest("valid-token");
        GoogleIdToken googleIdToken = createMockGoogleIdToken("anonymous.dev@gmail.com", "google-sub-anonymous", null, true);

        when(googleIdTokenVerifier.verify("valid-token")).thenReturn(googleIdToken);

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
    @DisplayName("Google OAuth throws BadCredentialsException when token verification returns null")
    void authenticateGoogle_InvalidToken_ThrowsBadCredentialsException() throws Exception {
        GoogleAuthRequest request = new GoogleAuthRequest("invalid-id-token");
        when(googleIdTokenVerifier.verify("invalid-id-token")).thenReturn(null);

        assertThrows(BadCredentialsException.class, () -> authService.authenticateGoogle(request));
    }

    @Test
    @DisplayName("Google OAuth throws BadCredentialsException when verification throws exception")
    void authenticateGoogle_VerificationException_ThrowsBadCredentialsException() throws Exception {
        GoogleAuthRequest request = new GoogleAuthRequest("corrupt-token");
        when(googleIdTokenVerifier.verify("corrupt-token")).thenThrow(new IOException("Network error"));

        assertThrows(BadCredentialsException.class, () -> authService.authenticateGoogle(request));
    }

    @Test
    @DisplayName("Google OAuth throws BadCredentialsException when email is unverified")
    void authenticateGoogle_UnverifiedEmail_ThrowsBadCredentialsException() throws Exception {
        GoogleAuthRequest request = new GoogleAuthRequest("unverified-email-token");
        GoogleIdToken googleIdToken = createMockGoogleIdToken("unverified@gmail.com", "google-sub-unverified", "Unverified User", false);

        when(googleIdTokenVerifier.verify("unverified-email-token")).thenReturn(googleIdToken);

        assertThrows(BadCredentialsException.class, () -> authService.authenticateGoogle(request));
    }

    @Test
    @DisplayName("Google OAuth throws AccountPendingApprovalException when account is PENDING_APPROVAL")
    void authenticateGoogle_PendingApproval_ThrowsAccountPendingApprovalException() throws Exception {
        GoogleAuthRequest request = new GoogleAuthRequest("valid-token");
        GoogleIdToken googleIdToken = createMockGoogleIdToken("pending@partner.lk", "google-sub-pending", "Pending Merchant", true);

        when(googleIdTokenVerifier.verify("valid-token")).thenReturn(googleIdToken);

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
    void authenticateGoogle_Suspended_ThrowsAccountSuspendedException() throws Exception {
        GoogleAuthRequest request = new GoogleAuthRequest("valid-token");
        GoogleIdToken googleIdToken = createMockGoogleIdToken("suspended@swiftdrop.lk", "google-sub-suspended", "Suspended User", true);

        when(googleIdTokenVerifier.verify("valid-token")).thenReturn(googleIdToken);

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
    void authenticateGoogle_Rejected_ThrowsAccountRejectedException() throws Exception {
        GoogleAuthRequest request = new GoogleAuthRequest("valid-token");
        GoogleIdToken googleIdToken = createMockGoogleIdToken("rejected@rider.lk", "google-sub-rejected", "Rejected Courier", true);

        when(googleIdTokenVerifier.verify("valid-token")).thenReturn(googleIdToken);

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
}
