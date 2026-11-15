package com.nyxora.swiftdrop.notification.webhook.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GatewaySecurityValidator Unit Tests")
class GatewaySecurityValidatorTest {

    private static final String DEV_SECRET = "dev-gateway-shared-secret-change-me";
    private static final String PROD_SECRET = "high-entropy-production-secret-9876543210fedcba";

    @Mock
    private Environment environment;

    @Test
    @DisplayName("Should authenticate valid merchant request with correct Gateway secret")
    void shouldAuthenticateValidMerchantRequest() {
        GatewaySecurityValidator validator = new GatewaySecurityValidator(environment, DEV_SECRET);

        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        var result = validator.authenticateMerchantRequest(
                DEV_SECRET,
                userId.toString(),
                "ROLE_MERCHANT",
                merchantId.toString(),
                null
        );

        assertInstanceOf(GatewaySecurityValidator.MerchantAuthResult.Success.class, result);
        var success = (GatewaySecurityValidator.MerchantAuthResult.Success) result;
        assertEquals(userId, success.userId());
        assertEquals(merchantId, success.merchantId());
        assertEquals("ROLE_MERCHANT", success.userRole());
    }

    @Test
    @DisplayName("Should accept valid ADMIN role for merchant-scoped endpoints")
    void shouldAcceptAdminRole() {
        GatewaySecurityValidator validator = new GatewaySecurityValidator(environment, DEV_SECRET);

        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        var result = validator.authenticateMerchantRequest(
                DEV_SECRET,
                userId.toString(),
                "ROLE_ADMIN",
                merchantId.toString(),
                null
        );

        assertInstanceOf(GatewaySecurityValidator.MerchantAuthResult.Success.class, result);
    }

    @Test
    @DisplayName("Should reject request with 401 when X-Gateway-Secret header is missing")
    void shouldRejectMissingGatewaySecret() {
        GatewaySecurityValidator validator = new GatewaySecurityValidator(environment, DEV_SECRET);

        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        var result = validator.authenticateMerchantRequest(
                null,
                userId.toString(),
                "ROLE_MERCHANT",
                merchantId.toString(),
                null
        );

        assertInstanceOf(GatewaySecurityValidator.MerchantAuthResult.Failure.class, result);
        var fail = (GatewaySecurityValidator.MerchantAuthResult.Failure) result;
        assertEquals(HttpStatus.UNAUTHORIZED, fail.status());
        assertEquals("INVALID_GATEWAY_SECRET", fail.error());
    }

    @Test
    @DisplayName("Should reject request with 401 when X-Gateway-Secret header is incorrect")
    void shouldRejectIncorrectGatewaySecret() {
        GatewaySecurityValidator validator = new GatewaySecurityValidator(environment, DEV_SECRET);

        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        var result = validator.authenticateMerchantRequest(
                "guessed-invalid-secret",
                userId.toString(),
                "ROLE_MERCHANT",
                merchantId.toString(),
                null
        );

        assertInstanceOf(GatewaySecurityValidator.MerchantAuthResult.Failure.class, result);
        var fail = (GatewaySecurityValidator.MerchantAuthResult.Failure) result;
        assertEquals(HttpStatus.UNAUTHORIZED, fail.status());
        assertEquals("INVALID_GATEWAY_SECRET", fail.error());
    }

    @Test
    @DisplayName("Should reject request with 401 when X-User-Id is missing or malformed")
    void shouldRejectMissingOrMalformedUserId() {
        GatewaySecurityValidator validator = new GatewaySecurityValidator(environment, DEV_SECRET);
        UUID merchantId = UUID.randomUUID();

        var missing = validator.authenticateMerchantRequest(
                DEV_SECRET, null, "ROLE_MERCHANT", merchantId.toString(), null);
        assertInstanceOf(GatewaySecurityValidator.MerchantAuthResult.Failure.class, missing);
        assertEquals(HttpStatus.UNAUTHORIZED, ((GatewaySecurityValidator.MerchantAuthResult.Failure) missing).status());
        assertEquals("UNAUTHENTICATED", ((GatewaySecurityValidator.MerchantAuthResult.Failure) missing).error());

        var malformed = validator.authenticateMerchantRequest(
                DEV_SECRET, "not-a-uuid", "ROLE_MERCHANT", merchantId.toString(), null);
        assertInstanceOf(GatewaySecurityValidator.MerchantAuthResult.Failure.class, malformed);
        assertEquals(HttpStatus.UNAUTHORIZED, ((GatewaySecurityValidator.MerchantAuthResult.Failure) malformed).status());
        assertEquals("UNAUTHENTICATED", ((GatewaySecurityValidator.MerchantAuthResult.Failure) malformed).error());
    }

    @Test
    @DisplayName("Should reject request with 403 when role is unauthorized (e.g. ROLE_CUSTOMER)")
    void shouldRejectUnauthorizedRole() {
        GatewaySecurityValidator validator = new GatewaySecurityValidator(environment, DEV_SECRET);

        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        var result = validator.authenticateMerchantRequest(
                DEV_SECRET,
                userId.toString(),
                "ROLE_CUSTOMER",
                merchantId.toString(),
                null
        );

        assertInstanceOf(GatewaySecurityValidator.MerchantAuthResult.Failure.class, result);
        var fail = (GatewaySecurityValidator.MerchantAuthResult.Failure) result;
        assertEquals(HttpStatus.FORBIDDEN, fail.status());
        assertEquals("FORBIDDEN", fail.error());
    }

    @Test
    @DisplayName("Should reject request with 400 when X-Merchant-Id is missing or malformed")
    void shouldRejectMissingOrMalformedMerchantId() {
        GatewaySecurityValidator validator = new GatewaySecurityValidator(environment, DEV_SECRET);
        UUID userId = UUID.randomUUID();

        var missing = validator.authenticateMerchantRequest(
                DEV_SECRET, userId.toString(), "ROLE_MERCHANT", null, null);
        assertInstanceOf(GatewaySecurityValidator.MerchantAuthResult.Failure.class, missing);
        assertEquals(HttpStatus.BAD_REQUEST, ((GatewaySecurityValidator.MerchantAuthResult.Failure) missing).status());
        assertEquals("MISSING_MERCHANT_IDENTITY", ((GatewaySecurityValidator.MerchantAuthResult.Failure) missing).error());

        var malformed = validator.authenticateMerchantRequest(
                DEV_SECRET, userId.toString(), "ROLE_MERCHANT", "not-a-uuid", null);
        assertInstanceOf(GatewaySecurityValidator.MerchantAuthResult.Failure.class, malformed);
        assertEquals(HttpStatus.BAD_REQUEST, ((GatewaySecurityValidator.MerchantAuthResult.Failure) malformed).status());
        assertEquals("MISSING_MERCHANT_IDENTITY", ((GatewaySecurityValidator.MerchantAuthResult.Failure) malformed).error());
    }

    @Test
    @DisplayName("Should reject request with 403 when candidate merchant identity does not match verified identity")
    void shouldRejectForgedMerchantIdentity() {
        GatewaySecurityValidator validator = new GatewaySecurityValidator(environment, DEV_SECRET);

        UUID userId = UUID.randomUUID();
        UUID realMerchantId = UUID.randomUUID();
        UUID forgedMerchantId = UUID.randomUUID();

        var result = validator.authenticateMerchantRequest(
                DEV_SECRET,
                userId.toString(),
                "ROLE_MERCHANT",
                realMerchantId.toString(),
                forgedMerchantId.toString()
        );

        assertInstanceOf(GatewaySecurityValidator.MerchantAuthResult.Failure.class, result);
        var fail = (GatewaySecurityValidator.MerchantAuthResult.Failure) result;
        assertEquals(HttpStatus.FORBIDDEN, fail.status());
        assertEquals("FORGED_MERCHANT_IDENTITY", fail.error());
    }

    @Test
    @DisplayName("Should accept request when candidate merchant matches verified merchant identity")
    void shouldAcceptMatchingCandidateMerchant() {
        GatewaySecurityValidator validator = new GatewaySecurityValidator(environment, DEV_SECRET);

        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        var result = validator.authenticateMerchantRequest(
                DEV_SECRET,
                userId.toString(),
                "ROLE_MERCHANT",
                merchantId.toString(),
                merchantId.toString()
        );

        assertInstanceOf(GatewaySecurityValidator.MerchantAuthResult.Success.class, result);
    }

    @Test
    @DisplayName("Should reject with 500 when running in production with dev default secret")
    void shouldRejectDevSecretInProduction() {
        when(environment.matchesProfiles("prod", "production")).thenReturn(true);

        GatewaySecurityValidator validator = new GatewaySecurityValidator(environment, DEV_SECRET);

        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        var result = validator.authenticateMerchantRequest(
                DEV_SECRET,
                userId.toString(),
                "ROLE_MERCHANT",
                merchantId.toString(),
                null
        );

        assertInstanceOf(GatewaySecurityValidator.MerchantAuthResult.Failure.class, result);
        var fail = (GatewaySecurityValidator.MerchantAuthResult.Failure) result;
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, fail.status());
        assertEquals("SECURITY_CONFIGURATION_ERROR", fail.error());
    }

    @Test
    @DisplayName("Should accept valid custom secret in production environment")
    void shouldAcceptCustomSecretInProduction() {
        when(environment.matchesProfiles("prod", "production")).thenReturn(true);

        GatewaySecurityValidator validator = new GatewaySecurityValidator(environment, PROD_SECRET);

        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        var result = validator.authenticateMerchantRequest(
                PROD_SECRET,
                userId.toString(),
                "ROLE_MERCHANT",
                merchantId.toString(),
                null
        );

        assertInstanceOf(GatewaySecurityValidator.MerchantAuthResult.Success.class, result);
    }
}
