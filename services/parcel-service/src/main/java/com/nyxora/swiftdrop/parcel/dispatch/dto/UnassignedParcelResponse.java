package com.nyxora.swiftdrop.parcel.dispatch.dto;

import com.nyxora.swiftdrop.parcel.domain.entity.Parcel;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO representing an unassigned parcel awaiting courier allocation.
 */
public record UnassignedParcelResponse(
        UUID id,
        String trackingNumber,
        String pickupAddress,
        String dropoffAddress,
        String recipientName,
        String recipientPhone,
        BigDecimal weightKg,
        ParcelSize parcelSize,
        BigDecimal estimatedFee,
        ParcelStatus currentStatus,
        Integer originZoneId,
        String originZoneCode,
        String originZoneName,
        Integer destinationZoneId,
        String destinationZoneCode,
        String destinationZoneName,
        Instant createdAt
) {
    public static UnassignedParcelResponse fromEntity(Parcel parcel) {
        return new UnassignedParcelResponse(
                parcel.getId(),
                parcel.getTrackingNumber(),
                parcel.getPickupAddress(),
                parcel.getDropoffAddress(),
                parcel.getRecipientName(),
                parcel.getRecipientPhone(),
                parcel.getWeightKg(),
                parcel.getParcelSize(),
                parcel.getEstimatedFee(),
                parcel.getCurrentStatus(),
                parcel.getOriginZone() != null ? parcel.getOriginZone().getId() : null,
                parcel.getOriginZone() != null ? parcel.getOriginZone().getZoneCode() : null,
                parcel.getOriginZone() != null ? parcel.getOriginZone().getZoneName() : null,
                parcel.getDestinationZone() != null ? parcel.getDestinationZone().getId() : null,
                parcel.getDestinationZone() != null ? parcel.getDestinationZone().getZoneCode() : null,
                parcel.getDestinationZone() != null ? parcel.getDestinationZone().getZoneName() : null,
                parcel.getCreatedAt()
        );
    }
}
