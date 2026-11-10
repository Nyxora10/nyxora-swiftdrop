package com.nyxora.swiftdrop.parcel.merchant;

import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

/**
 * Resolves the caller's merchant entity UUID from the gateway-injected {@code X-Merchant-Id}.
 *
 * <p>The API Gateway strips any client-supplied {@code X-Merchant-Id} and sets it only from the
 * {@code merchantId} claim of the verified JWT, so this value is the merchant the caller is
 * authenticated as. It is the same ID stored in {@code parcels.merchant_id}; the user account
 * UUID ({@code X-User-Id}) is a different value and must never be used to scope merchant data.</p>
 */
public final class MerchantIdentity {

    public static final String HEADER = "X-Merchant-Id";

    private MerchantIdentity() {
    }

    /**
     * @throws AccessDeniedException (HTTP 403) when no verified merchant identity is present
     */
    public static UUID require(String merchantIdHeader) {
        if (merchantIdHeader == null || merchantIdHeader.isBlank()) {
            throw new AccessDeniedException("Verified merchant identity is unavailable for this request.");
        }
        try {
            return UUID.fromString(merchantIdHeader.trim());
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException("Merchant identity header is malformed.");
        }
    }
}
