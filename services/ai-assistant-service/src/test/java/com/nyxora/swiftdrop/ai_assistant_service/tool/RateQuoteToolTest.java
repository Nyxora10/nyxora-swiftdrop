package com.nyxora.swiftdrop.ai_assistant_service.tool;

import com.nyxora.swiftdrop.ai_assistant_service.client.ParcelServiceClient;
import com.nyxora.swiftdrop.ai_assistant_service.dto.PublicDeliveryZoneResponse;
import com.nyxora.swiftdrop.ai_assistant_service.dto.quote.ParcelQuoteRequest;
import com.nyxora.swiftdrop.ai_assistant_service.dto.quote.ParcelQuoteResponse;
import com.nyxora.swiftdrop.ai_assistant_service.security.UserContextHolder;
import com.nyxora.swiftdrop.ai_assistant_service.security.UserIdentity;
import com.nyxora.swiftdrop.ai_assistant_service.service.DeliveryZoneResolver;
import com.nyxora.swiftdrop.ai_assistant_service.service.ZoneResolutionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RateQuoteToolTest {

    private DeliveryZoneResolver deliveryZoneResolver;
    private ParcelServiceClient parcelServiceClient;
    private RateQuoteTool rateQuoteTool;

    private final PublicDeliveryZoneResponse colomboZone =
            new PublicDeliveryZoneResponse(1, "WP-COL-01", "Colombo Central Metro", "Colombo");
    private final PublicDeliveryZoneResponse kandyZone =
            new PublicDeliveryZoneResponse(2, "CP-KAN-01", "Kandy Metro", "Kandy");

    @BeforeEach
    void setUp() {
        deliveryZoneResolver = mock(DeliveryZoneResolver.class);
        parcelServiceClient = mock(ParcelServiceClient.class);
        rateQuoteTool = new RateQuoteTool(deliveryZoneResolver, parcelServiceClient);
        UserContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    @DisplayName("Should return INVALID_INPUT when weight is null, zero, or negative")
    void shouldReturnInvalidInputForInvalidWeight() {
        RateQuoteResult nullWeight = rateQuoteTool.calculateRateQuote(null, "Colombo", "Kandy", "MEDIUM");
        assertThat(nullWeight.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);
        assertThat(nullWeight.message()).contains("positive number");

        RateQuoteResult zeroWeight = rateQuoteTool.calculateRateQuote(0.0, "Colombo", "Kandy", "MEDIUM");
        assertThat(zeroWeight.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);

        RateQuoteResult negativeWeight = rateQuoteTool.calculateRateQuote(-2.5, "Colombo", "Kandy", "MEDIUM");
        assertThat(negativeWeight.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);

        verifyNoInteractions(deliveryZoneResolver, parcelServiceClient);
    }

    @Test
    @DisplayName("Should return INVALID_INPUT when parcel size is missing or invalid")
    void shouldReturnInvalidInputForInvalidParcelSize() {
        RateQuoteResult blankSize = rateQuoteTool.calculateRateQuote(1.5, "Colombo", "Kandy", null);
        assertThat(blankSize.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);
        assertThat(blankSize.message()).contains("Parcel size is required");

        RateQuoteResult invalidSize = rateQuoteTool.calculateRateQuote(1.5, "Colombo", "Kandy", "HUGE");
        assertThat(invalidSize.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);
        assertThat(invalidSize.message()).contains("Valid options are SMALL, MEDIUM, or LARGE");

        verifyNoInteractions(deliveryZoneResolver, parcelServiceClient);
    }

    @Test
    @DisplayName("Should return UNAUTHORIZED when unauthenticated or customer attempts to get MERCHANT rate")
    void shouldRejectMerchantTierForNonMerchant() {
        // Anonymous caller
        RateQuoteResult anonymousResult = rateQuoteTool.calculateRateQuote(2.0, "Colombo", "Kandy", "MEDIUM", "MERCHANT");
        assertThat(anonymousResult.status()).isEqualTo(ToolResultStatus.UNAUTHORIZED);
        assertThat(anonymousResult.message()).contains("Merchant corporate discount pricing is restricted");

        // Customer caller
        UserContextHolder.setContext(new UserIdentity("cust-123", "ROLE_CUSTOMER", "customer@test.com"));
        RateQuoteResult customerResult = rateQuoteTool.calculateRateQuote(2.0, "Colombo", "Kandy", "MEDIUM", "MERCHANT");
        assertThat(customerResult.status()).isEqualTo(ToolResultStatus.UNAUTHORIZED);

        verifyNoInteractions(deliveryZoneResolver, parcelServiceClient);
    }

    @Test
    @DisplayName("Should allow MERCHANT tier for verified merchant account")
    void shouldAllowMerchantTierForMerchantRole() {
        UserContextHolder.setContext(new UserIdentity("merch-123", "ROLE_MERCHANT", "merchant@store.com"));

        when(deliveryZoneResolver.resolveZone("Colombo")).thenReturn(ZoneResolutionResult.resolved(colomboZone));
        when(deliveryZoneResolver.resolveZone("Kandy")).thenReturn(ZoneResolutionResult.resolved(kandyZone));

        ParcelQuoteResponse quote = new ParcelQuoteResponse(
                "MERCHANT",
                new BigDecimal("297.50"),
                new BigDecimal("106.25"),
                new BigDecimal("1.20"),
                new BigDecimal("50.00"),
                new BigDecimal("71.25"),
                BigDecimal.ZERO,
                new BigDecimal("534.50")
        );
        when(parcelServiceClient.calculateQuote(any(ParcelQuoteRequest.class))).thenReturn(quote);

        RateQuoteResult result = rateQuoteTool.calculateRateQuote(2.0, "Colombo", "Kandy", "MEDIUM", "MERCHANT");

        assertThat(result.status()).isEqualTo(ToolResultStatus.SUCCESS);
        assertThat(result.estimatedFee()).isEqualByComparingTo("534.50");
    }

    @Test
    @DisplayName("Should return INVALID_INPUT when origin zone is missing or not found")
    void shouldReturnInvalidInputWhenOriginNotFound() {
        when(deliveryZoneResolver.resolveZone("Atlantis"))
                .thenReturn(ZoneResolutionResult.notFound("Atlantis", "No delivery zone found matching 'Atlantis'."));

        RateQuoteResult result = rateQuoteTool.calculateRateQuote(2.0, "Atlantis", "Kandy", "SMALL");

        assertThat(result.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);
        assertThat(result.message()).contains("No delivery zone found matching origin");
    }

    @Test
    @DisplayName("Should return AMBIGUOUS with candidate zones when origin matches multiple zones")
    void shouldReturnAmbiguousWhenOriginMatchesMultipleZones() {
        PublicDeliveryZoneResponse zone1 = new PublicDeliveryZoneResponse(1, "WP-COL-01", "Colombo Central Metro", "Colombo");
        PublicDeliveryZoneResponse zone2 = new PublicDeliveryZoneResponse(3, "WP-COL-02", "Colombo South", "Colombo");

        when(deliveryZoneResolver.resolveZone("Colombo"))
                .thenReturn(ZoneResolutionResult.ambiguous("Colombo", List.of(zone1, zone2), "Multiple zones match"));

        RateQuoteResult result = rateQuoteTool.calculateRateQuote(2.0, "Colombo", "Kandy", "SMALL");

        assertThat(result.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);
        assertThat(result.availableZones()).hasSize(2);
        assertThat(result.message()).contains("Multiple delivery zones match origin 'Colombo'");
    }

    @Test
    @DisplayName("Should return INVALID_INPUT when destination zone is not found")
    void shouldReturnInvalidInputWhenDestinationNotFound() {
        when(deliveryZoneResolver.resolveZone("Colombo")).thenReturn(ZoneResolutionResult.resolved(colomboZone));
        when(deliveryZoneResolver.resolveZone("Nowhere"))
                .thenReturn(ZoneResolutionResult.notFound("Nowhere", "No delivery zone found matching 'Nowhere'."));

        RateQuoteResult result = rateQuoteTool.calculateRateQuote(2.0, "Colombo", "Nowhere", "SMALL");

        assertThat(result.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);
        assertThat(result.message()).contains("No delivery zone found matching destination");
    }

    @Test
    @DisplayName("Should return UNAVAILABLE when zone resolver indicates API is unavailable")
    void shouldReturnUnavailableWhenZoneLookupFails() {
        when(deliveryZoneResolver.resolveZone("Colombo"))
                .thenReturn(ZoneResolutionResult.unavailable("Zone lookup service is temporarily unavailable."));

        RateQuoteResult result = rateQuoteTool.calculateRateQuote(2.0, "Colombo", "Kandy", "SMALL");

        assertThat(result.status()).isEqualTo(ToolResultStatus.UNAVAILABLE);
    }

    @Test
    @DisplayName("Should return SUCCESS with verified fee, currency LKR, fee breakdown and no invented days")
    void shouldReturnSuccessWhenQuotationSucceeds() {
        when(deliveryZoneResolver.resolveZone("Colombo")).thenReturn(ZoneResolutionResult.resolved(colomboZone));
        when(deliveryZoneResolver.resolveZone("Kandy")).thenReturn(ZoneResolutionResult.resolved(kandyZone));

        ParcelQuoteResponse quote = new ParcelQuoteResponse(
                "STANDARD",
                new BigDecimal("350.00"),
                new BigDecimal("125.00"),
                new BigDecimal("1.20"),
                new BigDecimal("50.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("620.00")
        );
        when(parcelServiceClient.calculateQuote(any(ParcelQuoteRequest.class))).thenReturn(quote);

        RateQuoteResult result = rateQuoteTool.calculateRateQuote(2.5, "Colombo", "Kandy", "MEDIUM");

        assertThat(result.status()).isEqualTo(ToolResultStatus.SUCCESS);
        assertThat(result.originCity()).isEqualTo("Colombo Central Metro");
        assertThat(result.destinationCity()).isEqualTo("Kandy Metro");
        assertThat(result.estimatedFee()).isEqualByComparingTo("620.00");
        assertThat(result.currency()).isEqualTo("LKR");
        assertThat(result.estimatedDeliveryDays()).isNull(); // Must not invent delivery days
        assertThat(result.feeBreakdown()).isNotNull();
        assertThat(result.feeBreakdown().baseFee()).isEqualByComparingTo("350.00");
        assertThat(result.feeBreakdown().weightCharge()).isEqualByComparingTo("125.00");
    }

    @Test
    @DisplayName("Should return INVALID_INPUT explaining pricing rule unavailable when backend returns 400")
    void shouldReturnInvalidInputWhenPricingRuleNotFound() {
        when(deliveryZoneResolver.resolveZone("Colombo")).thenReturn(ZoneResolutionResult.resolved(colomboZone));
        when(deliveryZoneResolver.resolveZone("Kandy")).thenReturn(ZoneResolutionResult.resolved(kandyZone));

        HttpClientErrorException badRequest = HttpClientErrorException.create(
                HttpStatusCode.valueOf(400),
                "Bad Request",
                HttpHeaders.EMPTY,
                "No pricing rule found for origin=1, destination=2, size=LARGE".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        when(parcelServiceClient.calculateQuote(any(ParcelQuoteRequest.class))).thenThrow(badRequest);

        RateQuoteResult result = rateQuoteTool.calculateRateQuote(5.0, "Colombo", "Kandy", "LARGE");

        assertThat(result.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);
        assertThat(result.message()).contains("cannot currently be quoted. No pricing rule exists");
    }

    @Test
    @DisplayName("Should return UNAVAILABLE when ParcelServiceClient throws connection error")
    void shouldReturnUnavailableWhenPricingServiceFails() {
        when(deliveryZoneResolver.resolveZone("Colombo")).thenReturn(ZoneResolutionResult.resolved(colomboZone));
        when(deliveryZoneResolver.resolveZone("Kandy")).thenReturn(ZoneResolutionResult.resolved(kandyZone));

        when(parcelServiceClient.calculateQuote(any(ParcelQuoteRequest.class)))
                .thenThrow(new RestClientException("Connection timed out"));

        RateQuoteResult result = rateQuoteTool.calculateRateQuote(2.5, "Colombo", "Kandy", "MEDIUM");

        assertThat(result.status()).isEqualTo(ToolResultStatus.UNAVAILABLE);
        assertThat(result.message()).contains("temporarily unavailable");
    }

    @Test
    @DisplayName("Should allow MERCHANT tier for ROLE_ADMIN")
    void shouldAllowMerchantTierForAdminRole() {
        UserContextHolder.setContext(new UserIdentity("admin-1", "ROLE_ADMIN", "admin@swiftdrop.lk"));

        when(deliveryZoneResolver.resolveZone("Colombo")).thenReturn(ZoneResolutionResult.resolved(colomboZone));
        when(deliveryZoneResolver.resolveZone("Kandy")).thenReturn(ZoneResolutionResult.resolved(kandyZone));

        ParcelQuoteResponse quote = new ParcelQuoteResponse(
                "MERCHANT",
                new BigDecimal("297.50"),
                new BigDecimal("106.25"),
                new BigDecimal("1.20"),
                new BigDecimal("50.00"),
                new BigDecimal("71.25"),
                BigDecimal.ZERO,
                new BigDecimal("534.50")
        );
        when(parcelServiceClient.calculateQuote(any(ParcelQuoteRequest.class))).thenReturn(quote);

        RateQuoteResult result = rateQuoteTool.calculateRateQuote(2.0, "Colombo", "Kandy", "MEDIUM", "MERCHANT");

        assertThat(result.status()).isEqualTo(ToolResultStatus.SUCCESS);
    }

    @Test
    @DisplayName("Should handle EXPRESS tier and custom fallback tiers")
    void shouldHandleExpressAndFallbackTiers() {
        when(deliveryZoneResolver.resolveZone("Colombo")).thenReturn(ZoneResolutionResult.resolved(colomboZone));
        when(deliveryZoneResolver.resolveZone("Kandy")).thenReturn(ZoneResolutionResult.resolved(kandyZone));

        ParcelQuoteResponse quote = new ParcelQuoteResponse(
                "EXPRESS",
                new BigDecimal("450.00"),
                new BigDecimal("150.00"),
                new BigDecimal("1.20"),
                new BigDecimal("50.00"),
                BigDecimal.ZERO,
                new BigDecimal("100.00"),
                new BigDecimal("750.00")
        );
        when(parcelServiceClient.calculateQuote(any(ParcelQuoteRequest.class))).thenReturn(quote);

        RateQuoteResult expressResult = rateQuoteTool.calculateRateQuote(2.0, "Colombo", "Kandy", "MEDIUM", "EXPRESS");
        assertThat(expressResult.status()).isEqualTo(ToolResultStatus.SUCCESS);

        RateQuoteResult customTierResult = rateQuoteTool.calculateRateQuote(2.0, "Colombo", "Kandy", "MEDIUM", "CUSTOM_TIER");
        assertThat(customTierResult.status()).isEqualTo(ToolResultStatus.SUCCESS);

        RateQuoteResult blankTierResult = rateQuoteTool.calculateRateQuote(2.0, "Colombo", "Kandy", "MEDIUM", "   ");
        assertThat(blankTierResult.status()).isEqualTo(ToolResultStatus.SUCCESS);
    }

    @Test
    @DisplayName("Should return UNAVAILABLE when dependencies are null")
    void shouldReturnUnavailableWhenDependenciesNull() {
        RateQuoteTool noDepsTool = new RateQuoteTool(null, null);

        RateQuoteResult result = noDepsTool.calculateRateQuote(2.0, "Colombo", "Kandy", "MEDIUM");

        assertThat(result.status()).isEqualTo(ToolResultStatus.UNAVAILABLE);
    }

    @Test
    @DisplayName("Should handle origin MISSING_LOCATION and UNAVAILABLE")
    void shouldHandleOriginMissingLocationAndUnavailable() {
        when(deliveryZoneResolver.resolveZone("")).thenReturn(ZoneResolutionResult.missingLocation("Origin required"));
        RateQuoteResult missingResult = rateQuoteTool.calculateRateQuote(2.0, "", "Kandy", "SMALL");
        assertThat(missingResult.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);
        assertThat(missingResult.message()).contains("Origin city is required");

        when(deliveryZoneResolver.resolveZone("OfflineZone")).thenReturn(ZoneResolutionResult.unavailable("Service down"));
        RateQuoteResult unavailResult = rateQuoteTool.calculateRateQuote(2.0, "OfflineZone", "Kandy", "SMALL");
        assertThat(unavailResult.status()).isEqualTo(ToolResultStatus.UNAVAILABLE);
    }

    @Test
    @DisplayName("Should handle destination MISSING_LOCATION, AMBIGUOUS, and UNAVAILABLE")
    void shouldHandleDestinationEdgeCases() {
        when(deliveryZoneResolver.resolveZone("Colombo")).thenReturn(ZoneResolutionResult.resolved(colomboZone));

        when(deliveryZoneResolver.resolveZone("")).thenReturn(ZoneResolutionResult.missingLocation("Dest required"));
        RateQuoteResult missingDest = rateQuoteTool.calculateRateQuote(2.0, "Colombo", "", "SMALL");
        assertThat(missingDest.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);
        assertThat(missingDest.message()).contains("Destination city is required");

        PublicDeliveryZoneResponse zone1 = new PublicDeliveryZoneResponse(2, "CP-KAN-01", "Kandy Metro", "Kandy");
        PublicDeliveryZoneResponse zone2 = new PublicDeliveryZoneResponse(4, "CP-KAN-02", "Kandy South", "Kandy");
        when(deliveryZoneResolver.resolveZone("Kandy")).thenReturn(ZoneResolutionResult.ambiguous("Kandy", List.of(zone1, zone2), "Ambiguous"));
        RateQuoteResult ambDest = rateQuoteTool.calculateRateQuote(2.0, "Colombo", "Kandy", "SMALL");
        assertThat(ambDest.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);
        assertThat(ambDest.availableZones()).hasSize(2);

        when(deliveryZoneResolver.resolveZone("DownZone")).thenReturn(ZoneResolutionResult.unavailable("Down"));
        RateQuoteResult unavailDest = rateQuoteTool.calculateRateQuote(2.0, "Colombo", "DownZone", "SMALL");
        assertThat(unavailDest.status()).isEqualTo(ToolResultStatus.UNAVAILABLE);
    }

    @Test
    @DisplayName("Should return UNAVAILABLE when ParcelService returns 500 server error")
    void shouldReturnUnavailableOn500ServerError() {
        when(deliveryZoneResolver.resolveZone("Colombo")).thenReturn(ZoneResolutionResult.resolved(colomboZone));
        when(deliveryZoneResolver.resolveZone("Kandy")).thenReturn(ZoneResolutionResult.resolved(kandyZone));

        HttpClientErrorException serverError = HttpClientErrorException.create(
                HttpStatusCode.valueOf(500),
                "Internal Server Error",
                HttpHeaders.EMPTY,
                "Unexpected backend failure".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        when(parcelServiceClient.calculateQuote(any(ParcelQuoteRequest.class))).thenThrow(serverError);

        RateQuoteResult result = rateQuoteTool.calculateRateQuote(2.0, "Colombo", "Kandy", "MEDIUM");
        assertThat(result.status()).isEqualTo(ToolResultStatus.UNAVAILABLE);
    }

    @Test
    @DisplayName("Should support 4-argument calculateRateQuote overload")
    void shouldSupport4ArgOverload() {
        when(deliveryZoneResolver.resolveZone("Colombo")).thenReturn(ZoneResolutionResult.resolved(colomboZone));
        when(deliveryZoneResolver.resolveZone("Kandy")).thenReturn(ZoneResolutionResult.resolved(kandyZone));

        ParcelQuoteResponse quote = new ParcelQuoteResponse(
                "STANDARD",
                new BigDecimal("350.00"),
                new BigDecimal("125.00"),
                new BigDecimal("1.20"),
                new BigDecimal("50.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("620.00")
        );
        when(parcelServiceClient.calculateQuote(any(ParcelQuoteRequest.class))).thenReturn(quote);

        RateQuoteResult result = rateQuoteTool.calculateRateQuote(2.5, "Colombo", "Kandy", "MEDIUM");
        assertThat(result.status()).isEqualTo(ToolResultStatus.SUCCESS);
    }

    @Test
    @DisplayName("Should handle destination zone resolution branch outcomes")
    void shouldHandleDestinationZoneResolutionBranches() {
        when(deliveryZoneResolver.resolveZone("Colombo")).thenReturn(ZoneResolutionResult.resolved(colomboZone));

        // Missing destination
        when(deliveryZoneResolver.resolveZone("MissingDest")).thenReturn(ZoneResolutionResult.missingLocation("Missing dest"));
        RateQuoteResult missingRes = rateQuoteTool.calculateRateQuote(2.0, "Colombo", "MissingDest", "MEDIUM");
        assertThat(missingRes.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);

        // Destination not found
        when(deliveryZoneResolver.resolveZone("NotFoundDest")).thenReturn(ZoneResolutionResult.notFound("NotFoundDest", "Not found"));
        RateQuoteResult notFoundRes = rateQuoteTool.calculateRateQuote(2.0, "Colombo", "NotFoundDest", "MEDIUM");
        assertThat(notFoundRes.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);

        // Destination ambiguous
        when(deliveryZoneResolver.resolveZone("AmbiguousDest")).thenReturn(ZoneResolutionResult.ambiguous("AmbiguousDest", List.of(kandyZone), "Ambiguous"));
        RateQuoteResult ambiguousRes = rateQuoteTool.calculateRateQuote(2.0, "Colombo", "AmbiguousDest", "MEDIUM");
        assertThat(ambiguousRes.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);

        // Destination unavailable
        when(deliveryZoneResolver.resolveZone("UnavailableDest")).thenReturn(ZoneResolutionResult.unavailable("Service unavailable"));
        RateQuoteResult unavailRes = rateQuoteTool.calculateRateQuote(2.0, "Colombo", "UnavailableDest", "MEDIUM");
        assertThat(unavailRes.status()).isEqualTo(ToolResultStatus.UNAVAILABLE);
    }
}
