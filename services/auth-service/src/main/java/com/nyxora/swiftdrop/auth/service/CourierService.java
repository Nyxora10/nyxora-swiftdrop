package com.nyxora.swiftdrop.auth.service;

import com.nyxora.swiftdrop.auth.dto.request.CourierAvailabilityRequest;
import com.nyxora.swiftdrop.auth.dto.response.CourierProfileResponse;

import java.util.UUID;

/**
 * Domain service contract for courier self-service profile and availability management (Task 2.5).
 */
public interface CourierService {

    /**
     * Returns the authenticated courier's profile details.
     *
     * @param userId the authenticated user's UUID (from JWT subject)
     * @return courier profile response DTO
     */
    CourierProfileResponse getCourierProfile(UUID userId);

    /**
     * Toggles the courier's on/off-duty availability flag and optionally updates their zone.
     *
     * @param userId  the authenticated user's UUID (from JWT subject)
     * @param request availability flag and optional zone update
     * @return updated courier profile response DTO
     */
    CourierProfileResponse updateAvailability(UUID userId, CourierAvailabilityRequest request);
}
