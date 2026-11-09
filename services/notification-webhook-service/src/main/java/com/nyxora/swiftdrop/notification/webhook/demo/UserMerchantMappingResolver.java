package com.nyxora.swiftdrop.notification.webhook.demo;

import java.util.Optional;
import java.util.UUID;

/**
 * Strategy interface for resolving a verified merchant UUID from an authenticated user session.
 *
 * <p><strong>Security Architecture (Defense-in-Depth):</strong>
 * <ul>
 *   <li>Enforces zero-trust: Rejects relying on caller-controlled headers (such as raw {@code X-Merchant-Id}).</li>
 *   <li>Separation of concerns: Recognizes that {@code users.id} (user account) and {@code merchants.id}
 *       (merchant profile) are separate database identifiers.</li>
 *   <li>Fail-Closed: If no verified, trusted server-side mapping is available, implementations must return
 *       {@link Optional#empty()} to prevent unauthorized horizontal privilege escalation.</li>
 * </ul>
 * </p>
 */
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

