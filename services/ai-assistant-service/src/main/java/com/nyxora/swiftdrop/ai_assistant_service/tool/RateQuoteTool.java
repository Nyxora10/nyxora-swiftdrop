package com.nyxora.swiftdrop.ai_assistant_service.tool;

import com.nyxora.swiftdrop.ai_assistant_service.client.ParcelServiceClient;
import com.nyxora.swiftdrop.ai_assistant_service.dto.quote.ParcelQuoteRequest;
import com.nyxora.swiftdrop.ai_assistant_service.dto.quote.ParcelQuoteResponse;
import com.nyxora.swiftdrop.ai_assistant_service.security.UserContextHolder;
import com.nyxora.swiftdrop.ai_assistant_service.security.UserIdentity;
import com.nyxora.swiftdrop.ai_assistant_service.service.DeliveryZoneResolver;
import com.nyxora.swiftdrop.ai_assistant_service.service.ZoneResolutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * Spring-managed tool for calculating SwiftDrop delivery fee estimates and rate quotes.
 *
 * <p>Coordinates:
 * <ol>
 *   <li>Validation of parcel weight and size categories (SMALL, MEDIUM, LARGE).</li>
 *   <li>Resolution of origin location into a verified Sri Lankan delivery zone.</li>
 *   <li>Resolution of destination location into a verified Sri Lankan delivery zone.</li>
 *   <li>Disambiguation of multi-zone matches with structured clarification choices.</li>
 *   <li>Enforcement of tier-based authorization (preventing anonymous merchant rates).</li>
 *   <li>Execution of dynamic pricing via Parcel Service API ({@code POST /api/v1/parcels/quote}).</li>
 * </ol>
 */
@Component
public class RateQuoteTool {

    private static final Logger log =
            LoggerFactory.getLogger(RateQuoteTool.class);

    private static final Set<String> VALID_SIZES = Set.of("SMALL", "MEDIUM", "LARGE");

    private final DeliveryZoneResolver deliveryZoneResolver;
    private final ParcelServiceClient parcelServiceClient;

    public RateQuoteTool() {
        this(null, null);
    }

    @Autowired
    public RateQuoteTool(DeliveryZoneResolver deliveryZoneResolver, ParcelServiceClient parcelServiceClient) {
        this.deliveryZoneResolver = deliveryZoneResolver;
        this.parcelServiceClient = parcelServiceClient;
    }

    @Tool(name = "calculate_rate_quote",
          description = "Calculates SwiftDrop delivery fee quotation and itemized fee breakdown based on parcel weight, origin city or district, destination city or district, parcel size, and optional tier (STANDARD or EXPRESS).")
    public RateQuoteResult calculateRateQuote(
            @ToolParam(description = "Weight of the parcel in kilograms (e.g. 2.5)")
            Double parcelWeight,
            @ToolParam(description = "Origin or pickup city name, district, or zone code (e.g. Colombo, Colombo Central Metro, or WP-COL-01)")
            String originCity,
            @ToolParam(description = "Destination or delivery city name, district, or zone code (e.g. Kandy, Kandy Metro, or CP-KAN-01)")
            String destinationCity,
            @ToolParam(description = "Parcel size tier: SMALL, MEDIUM, or LARGE")
            String parcelSize,
            @ToolParam(description = "Optional pricing tier: STANDARD (default) or EXPRESS for same-day delivery. (MERCHANT is restricted to corporate accounts).", required = false)
            String tier) {

        log.info("Executing calculate_rate_quote for weight: {}, origin: {}, destination: {}, size: {}, tier: {}",
                parcelWeight, originCity, destinationCity, parcelSize, tier);

        // 1. Validate parcel weight
        if (parcelWeight == null || parcelWeight <= 0) {
            return RateQuoteResult.invalidInput("Parcel weight must be a positive number greater than 0 kg.");
        }

        // 2. Validate parcel size
        if (parcelSize == null || parcelSize.trim().isBlank()) {
            return RateQuoteResult.invalidInput("Parcel size is required. Please choose SMALL, MEDIUM, or LARGE.");
        }

        String normalizedSize = parcelSize.trim().toUpperCase();
        if (!VALID_SIZES.contains(normalizedSize)) {
            return RateQuoteResult.invalidInput(
                    "Invalid parcel size '" + parcelSize + "'. Valid options are SMALL, MEDIUM, or LARGE.");
        }

        // 3. Resolve tier and enforce authorization
        String appliedTier = (tier != null && !tier.trim().isBlank()) ? tier.trim().toUpperCase() : "STANDARD";
        if ("MERCHANT".equalsIgnoreCase(appliedTier)) {
            UserIdentity caller = UserContextHolder.getContext();
            boolean isMerchant = caller != null &&
                    ("ROLE_MERCHANT".equalsIgnoreCase(caller.role()) || "ROLE_ADMIN".equalsIgnoreCase(caller.role()));
            if (!isMerchant) {
                log.warn("Unauthorized attempt to query MERCHANT pricing by unauthenticated or non-merchant caller");
                return RateQuoteResult.unauthorized(
                        "Merchant corporate discount pricing is restricted to verified merchant accounts. Anonymous or standard customer accounts cannot access merchant rates. Standard and Express delivery tiers are available."
                );
            }
        } else if (!"EXPRESS".equalsIgnoreCase(appliedTier) && !"STANDARD".equalsIgnoreCase(appliedTier)) {
            appliedTier = "STANDARD";
        }

        // 4. Ensure dependencies are wired
        if (deliveryZoneResolver == null || parcelServiceClient == null) {
            return RateQuoteResult.unavailable(
                    "Live SwiftDrop pricing engine is currently synchronizing tariff tables and is temporarily unavailable."
            );
        }

        // 5. Resolve origin delivery zone
        ZoneResolutionResult originRes = deliveryZoneResolver.resolveZone(originCity);
        switch (originRes.status()) {
            case MISSING_LOCATION -> {
                return RateQuoteResult.invalidInput("Origin city is required. Please provide a pickup city or district.");
            }
            case NOT_FOUND -> {
                return RateQuoteResult.invalidInput("No delivery zone found matching origin '" + originCity + "'. Please specify another location or nearby major city.");
            }
            case AMBIGUOUS -> {
                return RateQuoteResult.ambiguous(
                        "Multiple delivery zones match origin '" + originCity + "'. Please choose one of the available zones by specifying its name or ID:",
                        originCity, destinationCity, originRes.candidateZones()
                );
            }
            case UNAVAILABLE -> {
                return RateQuoteResult.unavailable("Live SwiftDrop delivery zone lookup service is temporarily unavailable.");
            }
            case RESOLVED -> {
                // proceed
            }
        }

        // 6. Resolve destination delivery zone
        ZoneResolutionResult destRes = deliveryZoneResolver.resolveZone(destinationCity);
        switch (destRes.status()) {
            case MISSING_LOCATION -> {
                return RateQuoteResult.invalidInput("Destination city is required. Please provide a delivery city or district.");
            }
            case NOT_FOUND -> {
                return RateQuoteResult.invalidInput("No delivery zone found matching destination '" + destinationCity + "'. Please specify another location or nearby major city.");
            }
            case AMBIGUOUS -> {
                return RateQuoteResult.ambiguous(
                        "Multiple delivery zones match destination '" + destinationCity + "'. Please choose one of the available zones by specifying its name or ID:",
                        originCity, destinationCity, destRes.candidateZones()
                );
            }
            case UNAVAILABLE -> {
                return RateQuoteResult.unavailable("Live SwiftDrop delivery zone lookup service is temporarily unavailable.");
            }
            case RESOLVED -> {
                // proceed
            }
        }

        // 7. Call Parcel Service Quotation API
        ParcelQuoteRequest quoteRequest = new ParcelQuoteRequest(
                originRes.matchedZone().id(),
                destRes.matchedZone().id(),
                BigDecimal.valueOf(parcelWeight).setScale(2, RoundingMode.HALF_UP),
                normalizedSize,
                appliedTier
        );

        ParcelQuoteResponse quoteResponse;
        try {
            quoteResponse = parcelServiceClient.calculateQuote(quoteRequest);
        } catch (RestClientResponseException ex) {
            log.warn("Parcel quotation returned status {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().value() == 400) {
                return RateQuoteResult.invalidInput(
                        "This delivery route between " + originRes.matchedZone().zoneName() + " and "
                                + destRes.matchedZone().zoneName() + " cannot currently be quoted. No pricing rule exists for this combination."
                );
            }
            return RateQuoteResult.unavailable(
                    "Live SwiftDrop pricing engine is currently synchronizing tariff tables and is temporarily unavailable."
            );
        } catch (RestClientException ex) {
            log.error("Failed to connect to Parcel Service quotation API: {}", ex.getMessage(), ex);
            return RateQuoteResult.unavailable(
                    "Live SwiftDrop pricing engine is currently synchronizing tariff tables and is temporarily unavailable."
            );
        }

        // 8. Convert response to RateQuoteResult
        RateQuoteResult.FeeBreakdown feeBreakdown = new RateQuoteResult.FeeBreakdown(
                quoteResponse.baseFee(),
                quoteResponse.weightCharge(),
                quoteResponse.sizeMultiplier(),
                quoteResponse.zoneSurcharge(),
                quoteResponse.discountAmount(),
                quoteResponse.expressSurcharge(),
                quoteResponse.totalFee()
        );

        return RateQuoteResult.success(
                originRes.matchedZone().zoneName(),
                destRes.matchedZone().zoneName(),
                parcelWeight,
                normalizedSize,
                quoteResponse.totalFee(),
                feeBreakdown
        );
    }

    /**
     * Backward-compatible 4-argument overload for existing callers and tests.
     */
    public RateQuoteResult calculateRateQuote(
            Double parcelWeight,
            String originCity,
            String destinationCity,
            String parcelSize) {
        return calculateRateQuote(parcelWeight, originCity, destinationCity, parcelSize, "STANDARD");
    }
}
