package com.nyxora.swiftdrop.ai_assistant_service.tool;

import java.math.BigDecimal;

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
        Integer estimatedDeliveryDays
) {
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
                currency,
                estimatedDeliveryDays
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
                null
        );
    }
}

