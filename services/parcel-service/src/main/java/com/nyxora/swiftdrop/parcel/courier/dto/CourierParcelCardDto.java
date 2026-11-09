package com.nyxora.swiftdrop.parcel.courier.dto;

import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object representing an individual parcel assigned to a courier.
 * Rendered on mobile runsheet cards for pickups and deliveries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourierParcelCardDto {

    private UUID id;
    private String trackingNumber;
    private ParcelStatus currentStatus;
    private String originZoneName;
    private String destinationZoneName;
    private String pickupAddress;
    private String dropoffAddress;
    private String recipientName;
    private String recipientPhone;
    private BigDecimal weightKg;
    private ParcelSize parcelSize;
    private BigDecimal estimatedFee;
    private int failureCount;
    private Instant createdAt;
    private Instant updatedAt;
}