package com.nyxora.swiftdrop.auth.service;

import com.nyxora.swiftdrop.auth.config.JwtProperties;
import com.nyxora.swiftdrop.auth.dto.request.CourierRegisterRequest;
import com.nyxora.swiftdrop.auth.dto.request.CustomerRegisterRequest;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

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
}
