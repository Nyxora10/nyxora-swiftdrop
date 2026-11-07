package com.nyxora.swiftdrop.auth.service;

import com.nyxora.swiftdrop.auth.dto.request.MerchantWebhookUpdateRequest;
import com.nyxora.swiftdrop.auth.dto.request.OAuthTokenRequest;
import com.nyxora.swiftdrop.auth.dto.response.MerchantProfileResponse;
import com.nyxora.swiftdrop.auth.dto.response.MerchantWebhookConfigResponse;
import com.nyxora.swiftdrop.auth.dto.response.OAuthTokenResponse;
import com.nyxora.swiftdrop.auth.dto.response.RotateCredentialsResponse;

import java.util.UUID;

public interface MerchantAuthService {

    /**
     * Issues an OAuth 2.0 Bearer JWT for machine-to-machine integrations using client_credentials grant.
     */
    OAuthTokenResponse generateM2mToken(OAuthTokenRequest request);

    /**
     * Internal endpoint for notification-webhook-service to retrieve webhook config without cross-database access.
     */
    MerchantWebhookConfigResponse getInternalWebhookConfig(UUID merchantId);

    /**
     * Returns the authenticated merchant's profile, including masked secrets and client ID.
     */
    MerchantProfileResponse getMerchantProfile(UUID userId);

    /**
     * Updates the merchant's target webhook URL and optionally rotates the webhook signing secret.
     */
    MerchantProfileResponse updateWebhook(UUID userId, MerchantWebhookUpdateRequest request);

    /**
     * Rotates the OAuth 2.0 client secret, saving its BCrypt hash and returning the raw secret once.
     */
    RotateCredentialsResponse rotateCredentials(UUID userId);
}
