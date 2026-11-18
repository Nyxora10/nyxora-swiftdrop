package com.nyxora.swiftdrop.notification.webhook.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Validates the API Gateway shared secret and enforces zero-trust identity relay for protected webhook endpoints.
 *
 * <p><strong>Defense-in-Depth Architecture:</strong>
 * <ul>
 *   <li>Verifies the {@code X-Gateway-Secret} header stamped by the perimeter API Gateway.</li>
 *   <li>Uses constant-time byte comparison ({@link MessageDigest#isEqual}) to resist timing attacks.</li>
 *   <li>Rejects missing, blank, or development-default secrets when running in a production profile.</li>
 *   <li>Ensures caller identity ({@code X-User-Id}) and role ({@code X-User-Role}) are present and authorized.</li>
 *   <li>Enforces that {@code X-Merchant-Id} is verified by the Gateway and rejects caller-supplied forged IDs.</li>
 * </ul>
 * </p>
 */
@Component
public class GatewaySecurityValidator {

    private static final Logger log = LoggerFactory.getLogger(GatewaySecurityValidator.class);

    public static final String HEADER_GATEWAY_SECRET = "X-Gateway-Secret";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String HEADER_MERCHANT_ID = "X-Merchant-Id";

    public static final String DEFAULT_DEV_SECRET = "dev-gateway-shared-secret-change-me";

    private final Environment environment;
    private final String configuredSecret;

    @Autowired
    public GatewaySecurityValidator(
            @Autowired(required = false) Environment environment,
            @Value("${app.security.gateway-shared-secret:}") String configuredSecret) {
        this.environment = environment;
        this.configuredSecret = configuredSecret != null ? configuredSecret.trim() : "";

        if (this.configuredSecret.isEmpty()) {
            log.warn("[GatewaySecurityValidator] app.security.gateway-shared-secret is not configured; "
                    + "protected webhook endpoints will reject incoming requests.");
        } else if (DEFAULT_DEV_SECRET.equalsIgnoreCase(this.configuredSecret)) {
            if (isProductionEnvironment()) {
                log.error("[GatewaySecurityValidator] INSECURE CONFIGURATION: Default development gateway secret "
                        + "is configured in a production environment!");
            } else {
                log.info("[GatewaySecurityValidator] Running with development gateway shared secret.");
            }
        }
    }

    /**
     * Testing constructor allowing custom environment and secret.
     */
    public GatewaySecurityValidator(Environment environment) {
        this(environment, DEFAULT_DEV_SECRET);
    }

    /**
     * Authenticates and authorizes an incoming merchant-scoped request.
     *
     * @param gatewaySecretHeader    {@code X-Gateway-Secret} header from API Gateway
     * @param userIdHeader           {@code X-User-Id} header from API Gateway
     * @param userRoleHeader         {@code X-User-Role} header from API Gateway
     * @param merchantIdHeader       {@code X-Merchant-Id} header from API Gateway
     * @param candidateMerchantParam optional caller-supplied candidate merchant ID (anti-spoof checked)
     * @return {@link MerchantAuthResult.Success} with verified IDs or {@link MerchantAuthResult.Failure}
     */
    public MerchantAuthResult authenticateMerchantRequest(
            String gatewaySecretHeader,
            String userIdHeader,
            String userRoleHeader,
            String merchantIdHeader,
            String candidateMerchantParam) {

        // 1. Production security safeguard: reject if secret is missing or development default
        if (isProductionEnvironment()) {
            if (configuredSecret.isEmpty() || DEFAULT_DEV_SECRET.equalsIgnoreCase(configuredSecret)) {
                log.error("[GatewaySecurityValidator] Protected endpoint rejected: gateway-shared-secret is improperly "
                        + "configured for production environment");
                return new MerchantAuthResult.Failure(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "SECURITY_CONFIGURATION_ERROR",
                        "Gateway shared secret is improperly configured for production environment");
            }
        }

        // 2. Validate Gateway Shared Secret (Anti-Bypass / Anti-Spoofing)
        if (gatewaySecretHeader == null || gatewaySecretHeader.isBlank()) {
            log.warn("[GatewaySecurityValidator] Rejected request: missing X-Gateway-Secret header");
            return new MerchantAuthResult.Failure(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_GATEWAY_SECRET",
                    "Missing trusted Gateway secret header (X-Gateway-Secret)");
        }

        if (!matchesSecret(gatewaySecretHeader)) {
            log.warn("[GatewaySecurityValidator] Rejected request: invalid X-Gateway-Secret header");
            return new MerchantAuthResult.Failure(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_GATEWAY_SECRET",
                    "Invalid Gateway shared secret");
        }

        // 3. Authenticate Caller Identity (X-User-Id)
        UUID userId = parseUuid(userIdHeader);
        if (userId == null) {
            log.warn("[GatewaySecurityValidator] Rejected request: missing or malformed X-User-Id");
            return new MerchantAuthResult.Failure(
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHENTICATED",
                    "Missing or malformed trusted caller identity header (X-User-Id)");
        }

        // 4. Enforce Role-Based Access Control (X-User-Role)
        if (!isAuthorizedRole(userRoleHeader)) {
            log.warn("[GatewaySecurityValidator] Rejected unauthorized role [{}] for userId [{}]",
                    userRoleHeader, userId);
            return new MerchantAuthResult.Failure(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN",
                    "Access restricted to merchants and administrators");
        }

        // 5. Validate Verified Merchant Identity (X-Merchant-Id)
        UUID verifiedMerchantId = parseUuid(merchantIdHeader);
        if (verifiedMerchantId == null) {
            log.warn("[GatewaySecurityValidator] Rejected request: missing or malformed X-Merchant-Id for userId [{}]", userId);
            return new MerchantAuthResult.Failure(
                    HttpStatus.BAD_REQUEST,
                    "MISSING_MERCHANT_IDENTITY",
                    "Authenticated merchant request is missing verified X-Merchant-Id");
        }

        // 6. Anti-Spoofing Check: Caller-supplied merchant identity must not contradict verified X-Merchant-Id
        if (isForgedMerchantIdentity(candidateMerchantParam, verifiedMerchantId)) {
            log.warn("[GatewaySecurityValidator] Rejected forged merchant identity: caller supplied {}, verified is {}",
                    candidateMerchantParam, verifiedMerchantId);
            return new MerchantAuthResult.Failure(
                    HttpStatus.FORBIDDEN,
                    "FORGED_MERCHANT_IDENTITY",
                    "Caller-supplied merchant identity does not match verified merchant identity for authenticated user");
        }

        return new MerchantAuthResult.Success(userId, verifiedMerchantId, userRoleHeader);
    }

    public boolean isProductionEnvironment() {
        if (environment == null) {
            return false;
        }
        return environment.matchesProfiles("prod", "production");
    }

    private boolean matchesSecret(String providedSecret) {
        if (providedSecret == null || configuredSecret.isEmpty()) {
            return false;
        }
        byte[] expected = configuredSecret.getBytes(StandardCharsets.UTF_8);
        byte[] provided = providedSecret.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided);
    }

    private UUID parseUuid(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(headerValue.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean isAuthorizedRole(String userRoleHeader) {
        if (userRoleHeader == null || userRoleHeader.isBlank()) {
            return false;
        }
        return userRoleHeader.contains("MERCHANT") || userRoleHeader.contains("ADMIN");
    }

    private boolean isForgedMerchantIdentity(String candidate, UUID verifiedMerchantId) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        try {
            UUID supplied = UUID.fromString(candidate.trim());
            return !supplied.equals(verifiedMerchantId);
        } catch (IllegalArgumentException ex) {
            return true;
        }
    }

    /**
     * Sealed result hierarchy representing authentication and authorization outcomes.
     */
    public sealed interface MerchantAuthResult {
        record Success(UUID userId, UUID merchantId, String userRole) implements MerchantAuthResult {}
        record Failure(HttpStatus status, String error, String message) implements MerchantAuthResult {}
    }
}
