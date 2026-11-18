package com.nyxora.swiftdrop.auth.dto.response;

import com.nyxora.swiftdrop.auth.entity.Courier;
import lombok.Builder;

import java.util.UUID;

/**
 * Administrative view of an available courier and current workload for parcel dispatching.
 * Used by the Admin Dispatch Board (Member 5: Task 2.2).
 */
@Builder
public record AvailableCourierResponse(
        UUID courierId,
        UUID userId,
        String fullName,
        String email,
        String phoneNumber,
        String vehicleType,
        String licensePlate,
        int activeDeliveryCount,
        int maxWorkload,
        Integer assignedZoneId,
        boolean isAvailable,
        boolean canAssign
) {
    public static final int MAX_WORKLOAD_LIMIT = 20;

    /**
     * Maps a Courier entity with its associated User to an AvailableCourierResponse DTO.
     */
    public static AvailableCourierResponse fromEntity(Courier courier) {
        int activeCount = courier.getActiveDeliveryCount() != null ? courier.getActiveDeliveryCount() : 0;
        boolean available = Boolean.TRUE.equals(courier.getIsAvailable());
        boolean assignable = available && activeCount < MAX_WORKLOAD_LIMIT;

        return AvailableCourierResponse.builder()
                .courierId(courier.getId())
                .userId(courier.getUser() != null ? courier.getUser().getId() : null)
                .fullName(courier.getUser() != null ? courier.getUser().getFullName() : null)
                .email(courier.getUser() != null ? courier.getUser().getEmail() : null)
                .phoneNumber(courier.getUser() != null ? courier.getUser().getPhoneNumber() : null)
                .vehicleType(courier.getVehicleType() != null ? courier.getVehicleType().name() : null)
                .licensePlate(courier.getLicensePlate())
                .activeDeliveryCount(activeCount)
                .maxWorkload(MAX_WORKLOAD_LIMIT)
                .assignedZoneId(courier.getAssignedZoneId())
                .isAvailable(available)
                .canAssign(assignable)
                .build();
    }
}

