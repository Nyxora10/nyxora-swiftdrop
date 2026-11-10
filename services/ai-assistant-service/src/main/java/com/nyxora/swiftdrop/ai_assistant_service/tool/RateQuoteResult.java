package com.nyxora.swiftdrop.ai_assistant_service.tool;

import com.nyxora.swiftdrop.ai_assistant_service.dto.PublicDeliveryZoneResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * Structured result representing delivery rate quotations or operational status.
 */
public record RateQuoteResult(
        ToolResultStatus status,
        String message,
        String originCity,
        String destinationCity,
        Double weightKg,
        String parcelSize,
        BigDecimal estimatedFee,
        String currency,
        Integer estimatedDeliveryDays,
        List<PublicDeliveryZoneResponse> availableZones,
        FeeBreakdown feeBreakdown
) {
    /**
     * Itemized tariff breakdown from the Dynamic Pricing Strategy Engine.
     */
    public record FeeBreakdown(
            BigDecimal baseFee,
            BigDecimal weightCharge,
            BigDecimal sizeMultiplier,
            BigDecimal zoneSurcharge,
            BigDecimal discountAmount,
            BigDecimal expressSurcharge,
            BigDecimal totalFee
    ) {}

    public static RateQuoteResult success(
            String originCity,
            String destinationCity,
            Double weightKg,
            String parcelSize,
            BigDecimal totalFee,
            FeeBreakdown feeBreakdown) {
        return new RateQuoteResult(
                ToolResultStatus.SUCCESS,
                "Rate quote calculated successfully.",
                originCity,
                destinationCity,
                weightKg,
                parcelSize,
                totalFee,
                "LKR",
                null,
                null,
                feeBreakdown
        );
    }

    public static RateQuoteResult success(
            String originCity,
            String destinationCity,
            Double weightKg,
            String parcelSize,
            BigDecimal estimatedFee,
            String currency,
            Integer estimatedDeliveryDays) {
        return new RateQuoteResult(
                ToolResultStatus.SUCCESS,
                "Rate quote calculated successfully.",
                originCity,
                destinationCity,
                weightKg,
                parcelSize,
                estimatedFee,
                currency != null ? currency : "LKR",
                estimatedDeliveryDays,
                null,
                null
        );
    }

    public static RateQuoteResult ambiguous(
            String message,
            String originCity,
            String destinationCity,
            List<PublicDeliveryZoneResponse> availableZones) {
        return new RateQuoteResult(
                ToolResultStatus.INVALID_INPUT,
                message,
                originCity,
                destinationCity,
                null,
                null,
                null,
                null,
                null,
                availableZones,
                null
        );
    }

    public static RateQuoteResult invalidInput(String message) {
        return new RateQuoteResult(
                ToolResultStatus.INVALID_INPUT,
                message,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static RateQuoteResult unavailable(String message) {
        return new RateQuoteResult(
                ToolResultStatus.UNAVAILABLE,
                message,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static RateQuoteResult unauthorized(String message) {
        return new RateQuoteResult(
                ToolResultStatus.UNAUTHORIZED,
                message,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
