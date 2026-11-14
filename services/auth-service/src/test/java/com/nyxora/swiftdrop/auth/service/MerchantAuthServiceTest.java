package com.nyxora.swiftdrop.auth.service;

import com.nyxora.swiftdrop.auth.config.JwtProperties;
import com.nyxora.swiftdrop.auth.dto.request.MerchantWebhookUpdateRequest;
import com.nyxora.swiftdrop.auth.dto.request.OAuthTokenRequest;
import com.nyxora.swiftdrop.auth.dto.response.MerchantProfileResponse;
import com.nyxora.swiftdrop.auth.dto.response.MerchantWebhookConfigResponse;
import com.nyxora.swiftdrop.auth.dto.response.OAuthTokenResponse;
import com.nyxora.swiftdrop.auth.dto.response.RotateCredentialsResponse;
import com.nyxora.swiftdrop.auth.entity.Merchant;
import com.nyxora.swiftdrop.auth.entity.Role;
import com.nyxora.swiftdrop.auth.entity.User;
import com.nyxora.swiftdrop.auth.entity.enums.AuthProvider;
import com.nyxora.swiftdrop.auth.entity.enums.RoleName;
import com.nyxora.swiftdrop.auth.entity.enums.UserStatus;
import com.nyxora.swiftdrop.auth.exception.ResourceNotFoundException;
import com.nyxora.swiftdrop.auth.repository.MerchantRepository;
import com.nyxora.swiftdrop.auth.security.JwtTokenProvider;
import com.nyxora.swiftdrop.auth.service.impl.MerchantAuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MerchantAuthService Unit Tests")
class MerchantAuthServiceTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private MerchantAuthServiceImpl merchantAuthService;

    private User activeMerchantUser;
    private User pendingMerchantUser;
    private Merchant merchant;
    private UUID userId;
    private UUID merchantId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        merchantId = UUID.randomUUID();

        Role merchantRole = Role.builder().id(2).name(RoleName.ROLE_MERCHANT).build();

        activeMerchantUser = User.builder()
                .id(userId)
                .email("store@daraz.lk")
                .role(merchantRole)
                .fullName("Daraz Store")
                .status(UserStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .build();

        pendingMerchantUser = User.builder()
                .id(UUID.randomUUID())
                .email("pending@shop.lk")
                .role(merchantRole)
                .fullName("Pending Shop")
                .status(UserStatus.PENDING_APPROVAL)
                .authProvider(AuthProvider.LOCAL)
                .build();

        merchant = Merchant.builder()
                .id(merchantId)
                .user(activeMerchantUser)
                .businessName("Daraz Sri Lanka")
                .registrationNumber("PV-DARAZ-001")
                .oauthClientId("mer_daraz_client_01")
                .oauthClientSecretHash("$2a$12$hashedSecret")
                .webhookUrl("https://partner.daraz.lk/webhooks")
                .webhookSecret("whsec_secure_signing_secret_123")
                .build();
    }

    @Test
    @DisplayName("generateM2mToken succeeds with valid client_credentials")
    void generateM2mToken_ValidCredentials_Success() {
        OAuthTokenRequest request = new OAuthTokenRequest("client_credentials", "mer_daraz_client_01", "rawSecret123");

        when(merchantRepository.findByOauthClientId("mer_daraz_client_01")).thenReturn(Optional.of(merchant));
        when(passwordEncoder.matches("rawSecret123", "$2a$12$hashedSecret")).thenReturn(true);
        when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(jwtTokenProvider.generateM2mToken(activeMerchantUser, merchant)).thenReturn("mocked.jwt.token");

        OAuthTokenResponse response = merchantAuthService.generateM2mToken(request);

        assertNotNull(response);
        assertEquals("mocked.jwt.token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(900, response.expiresIn());
        assertEquals(JwtTokenProvider.M2M_SCOPES, response.scope());
        assertEquals(merchantId, response.merchantId());
    }

    @Test
    @DisplayName("generateM2mToken throws IllegalArgumentException on unsupported grant_type")
    void generateM2mToken_UnsupportedGrantType_ThrowsException() {
        OAuthTokenRequest request = new OAuthTokenRequest("authorization_code", "mer_daraz_client_01", "rawSecret123");

        assertThrows(IllegalArgumentException.class, () -> merchantAuthService.generateM2mToken(request));
        verifyNoInteractions(merchantRepository);
    }

    @Test
    @DisplayName("generateM2mToken throws BadCredentialsException when client_id not found")
    void generateM2mToken_ClientIdNotFound_ThrowsException() {
        OAuthTokenRequest request = new OAuthTokenRequest("client_credentials", "non_existent_client", "rawSecret123");
        when(merchantRepository.findByOauthClientId("non_existent_client")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> merchantAuthService.generateM2mToken(request));
    }

    @Test
    @DisplayName("generateM2mToken throws BadCredentialsException when merchant account is inactive")
    void generateM2mToken_InactiveAccount_ThrowsException() {
        Merchant pendingMerchant = Merchant.builder()
                .id(UUID.randomUUID())
                .user(pendingMerchantUser)
                .oauthClientId("mer_pending_01")
                .build();

        OAuthTokenRequest request = new OAuthTokenRequest("client_credentials", "mer_pending_01", "rawSecret123");
        when(merchantRepository.findByOauthClientId("mer_pending_01")).thenReturn(Optional.of(pendingMerchant));

        BadCredentialsException exception = assertThrows(BadCredentialsException.class,
                () -> merchantAuthService.generateM2mToken(request));
        assertTrue(exception.getMessage().contains("Merchant account is not active"));
    }

    @Test
    @DisplayName("generateM2mToken throws BadCredentialsException on password mismatch")
    void generateM2mToken_WrongSecret_ThrowsException() {
        OAuthTokenRequest request = new OAuthTokenRequest("client_credentials", "mer_daraz_client_01", "wrongSecret");

        when(merchantRepository.findByOauthClientId("mer_daraz_client_01")).thenReturn(Optional.of(merchant));
        when(passwordEncoder.matches("wrongSecret", "$2a$12$hashedSecret")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> merchantAuthService.generateM2mToken(request));
        verify(jwtTokenProvider, never()).generateM2mToken(any(), any());
    }

    @Test
    @DisplayName("getInternalWebhookConfig returns active configuration for notification service")
    void getInternalWebhookConfig_Success() {
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));

        MerchantWebhookConfigResponse response = merchantAuthService.getInternalWebhookConfig(merchantId);

        assertNotNull(response);
        assertEquals(merchantId, response.merchantId());
        assertEquals("https://partner.daraz.lk/webhooks", response.webhookUrl());
        assertEquals("whsec_secure_signing_secret_123", response.webhookSecret());
        assertTrue(response.isActive());
    }

    @Test
    @DisplayName("getInternalWebhookConfig throws ResourceNotFoundException when merchant missing")
    void getInternalWebhookConfig_NotFound_ThrowsException() {
        UUID unknownId = UUID.randomUUID();
        when(merchantRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> merchantAuthService.getInternalWebhookConfig(unknownId));
    }

    @Test
    @DisplayName("getMerchantProfile returns profile with masked webhook secret")
    void getMerchantProfile_Success() {
        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(merchant));

        MerchantProfileResponse response = merchantAuthService.getMerchantProfile(userId);

        assertNotNull(response);
        assertEquals(merchantId, response.merchantId());
        assertEquals("Daraz Sri Lanka", response.businessName());
        assertEquals("mer_daraz_client_01", response.oauthClientId());
        assertTrue(response.webhookSecret().startsWith("whsec_****"));
    }

    @Test
    @DisplayName("updateWebhook updates URL and regenerates signing secret when requested")
    void updateWebhook_WithRegeneration_Success() {
        MerchantWebhookUpdateRequest request = new MerchantWebhookUpdateRequest("https://new.daraz.lk/webhook", true);
        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(merchant));
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(i -> i.getArgument(0));

        MerchantProfileResponse response = merchantAuthService.updateWebhook(userId, request);

        assertNotNull(response);
        assertEquals("https://new.daraz.lk/webhook", response.webhookUrl());
        assertTrue(merchant.getWebhookSecret().startsWith("whsec_"));
        verify(merchantRepository, times(1)).save(merchant);
    }

    @Test
    @DisplayName("rotateCredentials generates new secret, stores hash, and returns plaintext secret once")
    void rotateCredentials_Success() {
        when(merchantRepository.findByUserId(userId)).thenReturn(Optional.of(merchant));
        when(passwordEncoder.encode(any(CharSequence.class))).thenReturn("$2a$12$newHashedSecret");
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(i -> i.getArgument(0));

        RotateCredentialsResponse response = merchantAuthService.rotateCredentials(userId);

        assertNotNull(response);
        assertEquals("mer_daraz_client_01", response.oauthClientId());
        assertNotNull(response.oauthClientSecret());
        assertTrue(response.oauthClientSecret().startsWith("cs_live_"));
        assertEquals("$2a$12$newHashedSecret", merchant.getOauthClientSecretHash());
        verify(merchantRepository, times(1)).save(merchant);
    }
}
