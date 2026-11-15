package com.nyxora.swiftdrop.auth.service.impl;

import com.nyxora.swiftdrop.auth.config.JwtProperties;
import com.nyxora.swiftdrop.auth.dto.request.MerchantWebhookUpdateRequest;
import com.nyxora.swiftdrop.auth.dto.request.OAuthTokenRequest;
import com.nyxora.swiftdrop.auth.dto.response.MerchantProfileResponse;
import com.nyxora.swiftdrop.auth.dto.response.MerchantWebhookConfigResponse;
import com.nyxora.swiftdrop.auth.dto.response.OAuthTokenResponse;
import com.nyxora.swiftdrop.auth.dto.response.RotateCredentialsResponse;
import com.nyxora.swiftdrop.auth.entity.Merchant;
import com.nyxora.swiftdrop.auth.entity.User;
import com.nyxora.swiftdrop.auth.entity.enums.UserStatus;
import com.nyxora.swiftdrop.auth.exception.ResourceNotFoundException;
import com.nyxora.swiftdrop.auth.repository.MerchantRepository;
import com.nyxora.swiftdrop.auth.security.JwtTokenProvider;
import com.nyxora.swiftdrop.auth.service.MerchantAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantAuthServiceImpl implements MerchantAuthService {

    private static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    @Override
    @Transactional(readOnly = true)
    public OAuthTokenResponse generateM2mToken(OAuthTokenRequest request) {
        if (!GRANT_TYPE_CLIENT_CREDENTIALS.equalsIgnoreCase(request.grantType())) {
            log.warn("[MerchantAuth] Unsupported grant_type: {}", request.grantType());
            throw new IllegalArgumentException("Unsupported grant_type: '" + request.grantType() + "'. Expected 'client_credentials'.");
        }

        Merchant merchant = merchantRepository.findByOauthClientId(request.clientId())
                .orElseThrow(() -> {
                    log.warn("[MerchantAuth] Client ID not found: {}", request.clientId());
                    return new BadCredentialsException("Invalid client_id or client_secret.");
                });

        User user = merchant.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("[MerchantAuth] Inactive merchant '{}' attempted M2M token request (status: {})",
                    merchant.getBusinessName(), user.getStatus());
            throw new BadCredentialsException("Merchant account is not active. Current status: " + user.getStatus());
        }

        if (merchant.getOauthClientSecretHash() == null ||
                !passwordEncoder.matches(request.clientSecret(), merchant.getOauthClientSecretHash())) {
            log.warn("[MerchantAuth] Invalid client secret for client_id: {}", request.clientId());
            throw new BadCredentialsException("Invalid client_id or client_secret.");
        }

        String accessToken = jwtTokenProvider.generateM2mToken(user, merchant);
        log.info("[MerchantAuth] Issued M2M Bearer token for merchant: {} (ID: {})",
                merchant.getBusinessName(), merchant.getId());

        return OAuthTokenResponse.of(accessToken, jwtProperties.getAccessTokenExpirationMs(), merchant.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public MerchantWebhookConfigResponse getInternalWebhookConfig(UUID merchantId) {
        log.debug("[MerchantAuth] Internal lookup for webhook config, merchantId: {}", merchantId);
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found with ID: " + merchantId));

        boolean isActive = merchant.getUser().getStatus() == UserStatus.ACTIVE;
        return new MerchantWebhookConfigResponse(
                merchant.getId(),
                merchant.getWebhookUrl(),
                merchant.getWebhookSecret(),
                isActive
        );
    }

    @Override
    @Transactional(readOnly = true)
    public MerchantProfileResponse getMerchantProfile(UUID userId) {
        Merchant merchant = merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant profile not found for user ID: " + userId));

        return toProfileResponse(merchant);
    }

    @Override
    @Transactional
    public MerchantProfileResponse updateWebhook(UUID userId, MerchantWebhookUpdateRequest request) {
        Merchant merchant = merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant profile not found for user ID: " + userId));

        if (request.webhookUrl() != null) {
            merchant.setWebhookUrl(request.webhookUrl().trim().isEmpty() ? null : request.webhookUrl().trim());
        }

        String generatedSecret = null;
        if (Boolean.TRUE.equals(request.regenerateSecret())) {
            generatedSecret = generateWebhookSecret();
            merchant.setWebhookSecret(generatedSecret);
            // Never log the secret itself.
            log.info("[MerchantAuth] Regenerated webhook secret for merchant: {}", merchant.getId());
        }

        Merchant saved = merchantRepository.save(merchant);
        MerchantProfileResponse profile = toProfileResponse(saved);
        if (generatedSecret == null) {
            return profile;
        }
        // Reveal the plaintext exactly once, in the response to the rotation request.
        return new MerchantProfileResponse(
                profile.merchantId(),
                profile.businessName(),
                profile.registrationNumber(),
                profile.oauthClientId(),
                profile.webhookUrl(),
                profile.webhookSecret(),
                generatedSecret
        );
    }

    private static String generateWebhookSecret() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "whsec_" + HexFormat.of().formatHex(bytes);
    }

    @Override
    @Transactional
    public RotateCredentialsResponse rotateCredentials(UUID userId) {
        Merchant merchant = merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant profile not found for user ID: " + userId));

        String rawSecret = "cs_live_" + UUID.randomUUID().toString().replace("-", "");
        merchant.setOauthClientSecretHash(passwordEncoder.encode(rawSecret));
        merchantRepository.save(merchant);

        log.info("[MerchantAuth] Rotated OAuth client secret for merchant: {}", merchant.getId());
        return new RotateCredentialsResponse(
                merchant.getOauthClientId(),
                rawSecret,
                "Client secret rotated successfully. Store this secret safely as it will not be displayed again."
        );
    }

    private MerchantProfileResponse toProfileResponse(Merchant merchant) {
        String rawSecret = merchant.getWebhookSecret();
        String masked = "whsec_****";
        if (rawSecret != null && !rawSecret.isBlank()) {
            masked = rawSecret.length() > 8 ? "whsec_****" + rawSecret.substring(rawSecret.length() - 4) : "whsec_****";
        }

        return new MerchantProfileResponse(
                merchant.getId(),
                merchant.getBusinessName(),
                merchant.getRegistrationNumber(),
                merchant.getOauthClientId(),
                merchant.getWebhookUrl(),
                masked
        );
    }
}
