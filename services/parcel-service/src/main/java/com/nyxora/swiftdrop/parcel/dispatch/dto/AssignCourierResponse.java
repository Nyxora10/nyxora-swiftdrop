package com.nyxora.swiftdrop.parcel.dispatch.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Confirmation payload returned after successful courier allocation.
 */
public record AssignCourierResponse(
        UUID parcelId,
        String trackingNumber,
        UUID assignedCourierId,
        String status,
        Instant assignedAt,
        String message
) {
}
