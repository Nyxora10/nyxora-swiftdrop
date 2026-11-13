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
 * Detailed summary of an individual completed or terminal delivery
 * performed by the authenticated courier (Member 6: Delivery History).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourierDeliveryHistoryItemDto {

    private UUID parcelId;
    private String trackingNumber;
    private ParcelStatus finalStatus;
    private String recipientName;
    private String recipientPhone;
    private String dropoffAddress;
    private String destinationZoneName;
    private BigDecimal weightKg;
    private ParcelSize parcelSize;
    private BigDecimal estimatedFee;
    private String recipientSignatureName;
    private String proofPhotoUrl;
    private String deliveryNote;
    private String failureReason;
    private Instant completedAt;
}