package com.nyxora.swiftdrop.auth.service;

import com.nyxora.swiftdrop.auth.config.JwtProperties;
import com.nyxora.swiftdrop.auth.entity.RefreshToken;
import com.nyxora.swiftdrop.auth.entity.User;
import com.nyxora.swiftdrop.auth.exception.InvalidRefreshTokenException;
import com.nyxora.swiftdrop.auth.repository.RefreshTokenRepository;
import com.nyxora.swiftdrop.auth.service.impl.RefreshTokenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@swiftdrop.lk")
                .fullName("Test User")
                .build();
    }

    @Test
    @DisplayName("Create refresh token persists hash and returns raw string")
    void createRefreshToken_Success() {
        when(jwtProperties.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        String rawToken = refreshTokenService.createRefreshToken(testUser);

        assertNotNull(rawToken);
        assertFalse(rawToken.isBlank());
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Verify and rotate detects token reuse and revokes all user sessions")
    void verifyAndRotate_RevokedToken_ReplayAttackDetected() {
        RefreshToken alreadyRevokedToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash("some_hash")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(true) // Attacker tries to use already-revoked token
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(alreadyRevokedToken));

        InvalidRefreshTokenException ex = assertThrows(InvalidRefreshTokenException.class, () ->
                refreshTokenService.verifyAndRotate("stolen_or_reused_raw_token")
        );

        assertTrue(ex.getMessage().contains("reuse detected") || ex.getMessage().contains("invalid"));
        // Verify all sessions were terminated for security
        verify(refreshTokenRepository, times(1)).revokeAllByUserId(testUser.getId());
    }

    @Test
    @DisplayName("Verify and rotate throws exception on expired token")
    void verifyAndRotate_ExpiredToken_ThrowsException() {
        RefreshToken expiredToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tokenHash("some_hash")
                .expiresAt(Instant.now().minusSeconds(3600)) // Expired in past
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expiredToken));

        assertThrows(InvalidRefreshTokenException.class, () ->
                refreshTokenService.verifyAndRotate("expired_raw_token")
        );
        verify(refreshTokenRepository, times(1)).save(expiredToken);
        assertTrue(expiredToken.getRevoked());
    }

    @Test
    @DisplayName("Revoke all by user ID delegates to repository")
    void revokeAllByUserId_DelegatesToRepository() {
        UUID userId = UUID.randomUUID();
        refreshTokenService.revokeAllByUserId(userId);
        verify(refreshTokenRepository, times(1)).revokeAllByUserId(userId);
    }
}

