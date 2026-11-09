package com.nyxora.swiftdrop.ai_assistant_service.tool;

import java.time.Instant;

/**
 * Structured result representing parcel tracking data or operational status.
 */
public record ParcelTrackingResult(
        ToolResultStatus status,
        String message,
        String trackingNumber,
        String currentStatus,
        String currentLocation,
        String recipientCity,
        Instant estimatedDelivery
) {
    public static ParcelTrackingResult success(
            String trackingNumber,
            String currentStatus,
            String currentLocation,
            String recipientCity,
            Instant estimatedDelivery) {
        return new ParcelTrackingResult(
                ToolResultStatus.SUCCESS,
                "Tracking information retrieved successfully.",
                trackingNumber,
                currentStatus,
                currentLocation,
                recipientCity,
                estimatedDelivery
        );
    }

    public static ParcelTrackingResult unavailable(String message) {
        return new ParcelTrackingResult(
                ToolResultStatus.UNAVAILABLE,
                message,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static ParcelTrackingResult invalidInput(String message) {
        return new ParcelTrackingResult(
                ToolResultStatus.INVALID_INPUT,
                message,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static ParcelTrackingResult unauthorized(String message) {
        return new ParcelTrackingResult(
                ToolResultStatus.UNAUTHORIZED,
                message,
                null,
                null,
                null,
                null,
                null
        );
    }
}

