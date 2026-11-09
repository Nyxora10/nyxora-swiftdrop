package com.nyxora.swiftdrop.parcel.courier.dto;

import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload returned after a parcel's delivery status is successfully transitioned.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParcelStatusUpdateResponseDto {

    private UUID parcelId;
    private String trackingNumber;
    private ParcelStatus previousStatus;
    private ParcelStatus currentStatus;
    private Instant updatedAt;
    private String message;
}