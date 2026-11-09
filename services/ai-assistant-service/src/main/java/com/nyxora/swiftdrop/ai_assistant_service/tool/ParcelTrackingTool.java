package com.nyxora.swiftdrop.ai_assistant_service.tool;

import com.nyxora.swiftdrop.ai_assistant_service.client.ParcelServiceClient;
import com.nyxora.swiftdrop.ai_assistant_service.dto.tracking.ParcelTrackingResponseDto;
import com.nyxora.swiftdrop.ai_assistant_service.security.UserContextHolder;
import com.nyxora.swiftdrop.ai_assistant_service.security.UserIdentity;
import com.nyxora.swiftdrop.common.security.util.PiiMaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Spring-managed read-only tool for retrieving SwiftDrop parcel tracking information.
 *
 * <p>Security & Privacy:
 * <ul>
 *   <li>Enforces identity isolation: caller identity is derived strictly from {@link UserContextHolder}.</li>
 *   <li>Privacy Minimization: Recipient contact information (names, phones, physical addresses)
 *       is completely stripped before sending data to Gemini.</li>
 *   <li>Delivery Note Sanitization: Free-text timeline notes are sanitized using {@link PiiMaskingUtil}
 *       to prevent sensitive numbers, emails, or street details from leaking to the model.</li>
 * </ul>
 */
@Component
public class ParcelTrackingTool {

    private static final Logger log =
            LoggerFactory.getLogger(ParcelTrackingTool.class);

    // Matches standard linear tracking numbers (SD-YYYYMMDD-XXXXXX) or legacy formats (SWIFT-XXXX)
    private static final Pattern TRACKING_PATTERN = Pattern.compile(
            "^(?i)(SD-\\d{8}-[A-Z0-9]{6}|SWIFT-[A-Z0-9]{4,12}|SD-[A-Z0-9-]{6,20})$"
    );

    private final ParcelServiceClient parcelServiceClient;

    public ParcelTrackingTool() {
        this(null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ParcelTrackingTool(ParcelServiceClient parcelServiceClient) {
        this.parcelServiceClient = parcelServiceClient;
    }

    @Tool(name = "get_parcel_tracking",
          description = "Retrieves SwiftDrop parcel tracking information including current delivery status, origin zone, destination zone, and chronological milestone history using a tracking number.")
    public ParcelTrackingResult getParcelTracking(
            @ToolParam(description = "The SwiftDrop tracking number (e.g. SD-20261107-7K9P2X or SWIFT-100234)")
            String trackingNumber) {

        // Identity isolation: Obtain caller identity strictly from trusted security context.
        UserIdentity caller = UserContextHolder.getContext();
        if (caller != null) {
            log.info("Parcel tracking requested by trusted identity: userId={}, role={}",
                    caller.userId(), caller.role());
        }

        if (trackingNumber == null || trackingNumber.trim().isBlank()) {
            return ParcelTrackingResult.invalidInput("Tracking number must not be empty.");
        }

        String normalized = trackingNumber.trim().toUpperCase();
        log.info("Executing parcel tracking tool for: {}", PiiMaskingUtil.maskText(normalized));

        // Format validation for SwiftDrop tracking identifier
        if (!TRACKING_PATTERN.matcher(normalized).matches()) {
            return ParcelTrackingResult.invalidInput(
                    "Invalid tracking number format. Format must be SD-YYYYMMDD-XXXXXX or SWIFT-XXXX.");
        }

        // Live downstream parcel service integration
        if (parcelServiceClient == null) {
            return ParcelTrackingResult.unavailable(
                    "Live SwiftDrop parcel tracking service is currently synchronizing with the warehouse network and is temporarily unavailable."
            );
        }

        Optional<ParcelTrackingResponseDto> trackingOpt;
        try {
            trackingOpt = parcelServiceClient.getTracking(normalized);
        } catch (RestClientException ex) {
            log.error("Failed to connect to parcel service for tracking number {}: {}",
                    PiiMaskingUtil.maskText(normalized), ex.getMessage());
            return ParcelTrackingResult.unavailable(
                    "Live SwiftDrop parcel tracking service is currently synchronizing with the warehouse network and is temporarily unavailable."
            );
        }

        if (trackingOpt.isEmpty()) {
            return ParcelTrackingResult.invalidInput(
                    "Parcel with tracking number '" + normalized + "' was not found.");
        }

        ParcelTrackingResponseDto parcel = trackingOpt.get();

        // Sanitize timeline milestones and strip any PII from delivery notes
        List<ParcelTrackingResult.MilestoneResult> sanitizedTimeline =
                parcel.timeline() != null
                        ? parcel.timeline().stream()
                                .map(event -> new ParcelTrackingResult.MilestoneResult(
                                        event.status(),
                                        event.timestamp(),
                                        sanitizeDeliveryNote(event.deliveryNote(), event.status())
                                ))
                                .toList()
                        : List.of();

        // Prefer human-readable zone names over technical zone codes
        String originZone = (parcel.originZoneName() != null && !parcel.originZoneName().isBlank())
                ? parcel.originZoneName()
                : parcel.originZoneCode();

        String destinationZone = (parcel.destinationZoneName() != null && !parcel.destinationZoneName().isBlank())
                ? parcel.destinationZoneName()
                : parcel.destinationZoneCode();

        // Return strictly necessary operational fields to Gemini (omits recipient name, phone, and address)
        return ParcelTrackingResult.success(
                parcel.trackingNumber(),
                parcel.currentStatus(),
                originZone,
                destinationZone,
                sanitizedTimeline
        );
    }

    /**
     * Sanitizes free-text delivery notes entered by couriers or dispatchers
     * by masking any embedded emails, phone numbers, or house numbers.
     */
    private String sanitizeDeliveryNote(String note, String status) {
        if (note == null || note.isBlank()) {
            return "Status updated to " + (status != null ? status : "UNKNOWN");
        }
        String masked = PiiMaskingUtil.maskText(note.trim());
        return PiiMaskingUtil.maskAddress(masked);
    }
}
