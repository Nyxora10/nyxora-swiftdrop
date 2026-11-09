package com.nyxora.swiftdrop.auth.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload for the courier shift availability toggle endpoint (Task 2.5).
 * Optionally updates the courier's operating zone alongside the availability flag.
 */
public record CourierAvailabilityRequest(

        @NotNull(message = "isAvailable flag must be provided (true = on-duty, false = off-duty)")
        Boolean isAvailable,

        /**
         * Optional. When provided, updates the courier's assigned delivery zone.
         * Nullable — omit to keep the existing zone unchanged.
         */
        Integer assignedZoneId
) {}
