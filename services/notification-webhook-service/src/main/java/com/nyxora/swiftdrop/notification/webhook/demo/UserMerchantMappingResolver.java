package com.nyxora.swiftdrop.notification.webhook.demo;

import java.util.Optional;
import java.util.UUID;

/**
 * Strategy interface for resolving a verified merchant UUID from an authenticated user session.
 *
 * <p><strong>Note:</strong> With Gateway claim enrichment (Task 4.3) and Auth Service JWT embedding (Task 2.5),
 * the API Gateway validates user sessions, extracts the {@code merchantId} claim, and relays verified
 * {@code X-Merchant-Id} headers stamped with {@code X-Gateway-Secret}.
 * Protected endpoints now authenticate through {@link com.nyxora.swiftdrop.notification.webhook.security.GatewaySecurityValidator}.
 * This interface is retained for backward compatibility.
 * </p>
 *
 * @deprecated Superceded by Gateway identity relay and {@link com.nyxora.swiftdrop.notification.webhook.security.GatewaySecurityValidator}.
 */
@Deprecated(since = "dev-gateway-enrichment")
@FunctionalInterface
public interface UserMerchantMappingResolver {

    /**
     * Resolves the verified merchant UUID corresponding to the given authenticated user UUID.
     *
     * @param userId the verified user UUID from the authenticated JWT session
     * @return Optional containing the verified merchant UUID if confirmed; empty if mapping is unavailable or unverified
     */
    Optional<UUID> resolveMerchantId(UUID userId);
}
