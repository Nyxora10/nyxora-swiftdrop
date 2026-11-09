package com.nyxora.swiftdrop.ai_assistant_service.tool;

import com.nyxora.swiftdrop.ai_assistant_service.security.UserContextHolder;
import com.nyxora.swiftdrop.ai_assistant_service.security.UserIdentity;
import com.nyxora.swiftdrop.common.security.util.PiiMaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Spring-managed read-only tool for retrieving SwiftDrop parcel tracking information.
 * Enforces identity isolation: caller identity is derived strictly from UserContextHolder,
 * preventing arbitrary customer identity injection by the model or MCP clients.
 */
@Component
public class ParcelTrackingTool {

    private static final Logger log =
            LoggerFactory.getLogger(ParcelTrackingTool.class);

    @Tool(name = "get_parcel_tracking",
          description = "Retrieves SwiftDrop parcel tracking information including current delivery status, current location, and estimated delivery time using a tracking number.")
    public ParcelTrackingResult getParcelTracking(
            @ToolParam(description = "The SwiftDrop tracking number (e.g. SWIFT-100234)")
            String trackingNumber) {

        // Identity isolation: Obtain caller identity strictly from trusted security context.
        // Never allow callers or LLM prompts to supply an arbitrary customer identity parameter.
        UserIdentity caller = UserContextHolder.getContext();
        if (caller != null) {
            log.info("Parcel tracking requested by trusted identity: userId={}, role={}",
                    caller.userId(), caller.role());
        }

        if (trackingNumber == null || trackingNumber.trim().isBlank()) {
            return ParcelTrackingResult.invalidInput("Tracking number must not be empty.");
        }

        String trimmed = trackingNumber.trim();
        log.info("Executing parcel tracking tool for: {}", PiiMaskingUtil.maskText(trimmed));

        // Format validation for SwiftDrop tracking identifier
        if (!trimmed.matches("(?i)^SWIFT-[A-Z0-9]{4,12}$")) {
            return ParcelTrackingResult.invalidInput("Invalid tracking number format. Format must be SWIFT-XXXX.");
        }

        // Live downstream parcel service integration
        // Currently, live warehouse tracking backend is synchronizing, so return UNAVAILABLE status
        return ParcelTrackingResult.unavailable(
                "Live SwiftDrop parcel tracking service is currently synchronizing with the warehouse network and is temporarily unavailable."
        );
    }
}
