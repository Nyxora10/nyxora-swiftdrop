package com.nyxora.swiftdrop.ai_assistant_service.tool;

import java.time.Instant;
import java.util.List;

/**
 * Structured result representing parcel tracking data supplied to the LLM (Gemini).
 *
 * <p>Privacy Enforcement:
 * Strips all recipient Personally Identifiable Information (PII) including masked names,
 * phone numbers, and delivery street addresses. Only operational routing data (zone names,
 * status, tracking identifier, and sanitized milestones) is exposed to the AI model.
 */
public record ParcelTrackingResult(
        ToolResultStatus status,
        String message,
        String trackingNumber,
        String currentStatus,
        String originZone,
        String destinationZone,
        List<MilestoneResult> timeline
) {
    /**
     * Sanitized timeline milestone record.
     */
    public record MilestoneResult(
            String status,
            Instant timestamp,
            String deliveryNote
    ) {}

    public static ParcelTrackingResult success(
            String trackingNumber,
            String currentStatus,
            String originZone,
            String destinationZone,
            List<MilestoneResult> timeline) {
        return new ParcelTrackingResult(
                ToolResultStatus.SUCCESS,
                "Tracking information retrieved successfully.",
                trackingNumber,
                currentStatus,
                originZone,
                destinationZone,
                timeline
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
