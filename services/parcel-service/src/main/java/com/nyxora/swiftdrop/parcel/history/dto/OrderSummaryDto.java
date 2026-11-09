package com.nyxora.swiftdrop.parcel.history.dto;

import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Summary view of a parcel delivery order for customer and merchant order history listings.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Order History Summary View")
public class OrderSummaryDto {

    @Schema(description = "Parcel UUID", example = "9dc3fc04-5d14-4dd8-b93d-2b19474a49a6")
    private UUID parcelId;

    @Schema(description = "Linear tracking number", example = "SD-20261107-7K9P2X")
    private String trackingNumber;

    @Schema(description = "Current lifecycle status", example = "DELIVERED")
    private ParcelStatus currentStatus;

    @Schema(description = "Recipient name", example = "Kamal Perera")
    private String recipientName;

    @Schema(description = "Recipient phone", example = "0771234567")
    private String recipientPhone;

    @Schema(description = "Dropoff street address", example = "No. 128, Kandy Road, Peradeniya")
    private String dropoffAddress;

    @Schema(description = "Origin zone name", example = "Colombo Central")
    private String originZoneName;

    @Schema(description = "Destination zone name", example = "Kandy Metro")
    private String destinationZoneName;

    @Schema(description = "Weight in kilograms", example = "2.50")
    private BigDecimal weightKg;

    @Schema(description = "Size classification", example = "MEDIUM")
    private ParcelSize parcelSize;

    @Schema(description = "Estimated delivery fee in LKR", example = "530.00")
    private BigDecimal estimatedFee;

    @Builder.Default
    @Schema(description = "Currency", example = "LKR")
    private String currency = "LKR";

    @Schema(description = "Creation timestamp (UTC)", example = "2026-11-07T09:15:00Z")
    private Instant createdAt;

    @Schema(description = "Last modification timestamp (UTC)", example = "2026-11-07T14:30:00Z")
    private Instant updatedAt;
}

