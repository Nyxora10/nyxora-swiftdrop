package com.nyxora.swiftdrop.auth.dto.response;

import com.nyxora.swiftdrop.auth.entity.Courier;
import com.nyxora.swiftdrop.auth.entity.enums.VehicleType;

import java.util.UUID;

/**
 * Read-only projection of a Courier's self-service profile (Task 2.5).
 * Returned by GET /api/v1/auth/courier/profile and PATCH /api/v1/auth/courier/availability.
 */
public record CourierProfileResponse(

        UUID id,
        String vehicleType,
        String licensePlate,
        Boolean isAvailable,
        Integer activeDeliveryCount,
        Integer assignedZoneId
) {
    /**
     * Maps a Courier JPA entity to this response record.
     */
    public static CourierProfileResponse fromEntity(Courier courier) {
        return new CourierProfileResponse(
                courier.getId(),
                courier.getVehicleType() != null ? courier.getVehicleType().name() : null,
                courier.getLicensePlate(),
                courier.getIsAvailable(),
                courier.getActiveDeliveryCount(),
                courier.getAssignedZoneId()
        );
    }
}
