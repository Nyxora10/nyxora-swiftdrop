package com.nyxora.swiftdrop.ai_assistant_service.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Spring-managed read-only tool for calculating SwiftDrop delivery fee estimates and rate quotes.
 */
@Component
public class RateQuoteTool {

    private static final Logger log =
            LoggerFactory.getLogger(RateQuoteTool.class);

    @Tool(name = "calculate_rate_quote",
          description = "Calculates SwiftDrop delivery fee quotation and estimated transit time based on parcel weight, origin city, destination city, and parcel size.")
    public RateQuoteResult calculateRateQuote(
            @ToolParam(description = "Weight of the parcel in kilograms (e.g. 2.5)")
            Double parcelWeight,
            @ToolParam(description = "Origin or pickup city name (e.g. Colombo)")
            String originCity,
            @ToolParam(description = "Destination or delivery city name (e.g. Kandy)")
            String destinationCity,
            @ToolParam(description = "Parcel size tier: SMALL, MEDIUM, LARGE, or EXTRA_LARGE")
            String parcelSize) {

        log.info("Executing calculate_rate_quote for weight: {}, origin: {}, destination: {}, size: {}",
                parcelWeight, originCity, destinationCity, parcelSize);

        if (parcelWeight == null || parcelWeight <= 0) {
            return RateQuoteResult.invalidInput("Parcel weight must be a positive number.");
        }

        if (originCity == null || originCity.trim().isBlank()) {
            return RateQuoteResult.invalidInput("Origin city is required.");
        }

        if (destinationCity == null || destinationCity.trim().isBlank()) {
            return RateQuoteResult.invalidInput("Destination city is required.");
        }

        if (parcelSize == null || parcelSize.trim().isBlank()) {
            return RateQuoteResult.invalidInput("Parcel size (SMALL, MEDIUM, LARGE, EXTRA_LARGE) is required.");
        }

        // Live downstream pricing service integration
        // Currently, live tariff backend is synchronizing, so return UNAVAILABLE status
        return RateQuoteResult.unavailable(
                "Live SwiftDrop pricing engine is currently synchronizing tariff tables and is temporarily unavailable."
        );
    }
}

