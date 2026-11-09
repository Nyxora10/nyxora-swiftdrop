package com.nyxora.swiftdrop.parcel.booking.dto;

import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import com.nyxora.swiftdrop.parcel.pricing.QuoteTier;
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
 * Detailed parcel booking confirmation returned to the customer upon successful booking.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Customer Parcel Booking Confirmation Response")
public class BookingResponse {

    @Schema(description = "Unique parcel UUID", example = "9dc3fc04-5d14-4dd8-b93d-2b19474a49a6")
    private UUID parcelId;

    @Schema(description = "Unique linear tracking number", example = "SD-20261107-7K9P2X")
    private String trackingNumber;

    @Schema(description = "Current lifecycle status", example = "CREATED")
    private ParcelStatus currentStatus;

    @Schema(description = "Sender user UUID", example = "9dc3fc04-5d14-4dd8-b93d-2b19474a49a6")
    private UUID senderUserId;

    @Schema(description = "Associated merchant UUID if corporate booking", example = "null")
    private UUID merchantId;

    @Schema(description = "Origin zone ID", example = "1")
    private Integer originZoneId;

    @Schema(description = "Origin zone code", example = "WP-COL-01")
    private String originZoneCode;

    @Schema(description = "Origin zone name", example = "Colombo Central")
    private String originZoneName;

    @Schema(description = "Destination zone ID", example = "2")
    private Integer destinationZoneId;

    @Schema(description = "Destination zone code", example = "CP-KAN-01")
    private String destinationZoneCode;

    @Schema(description = "Destination zone name", example = "Kandy Metro")
    private String destinationZoneName;

    @Schema(description = "Pickup address", example = "No. 45, Galle Road, Colombo 03")
    private String pickupAddress;

    @Schema(description = "Dropoff address", example = "No. 128, Kandy Road, Peradeniya")
    private String dropoffAddress;

    @Schema(description = "Recipient name", example = "Kamal Perera")
    private String recipientName;

    @Schema(description = "Recipient phone number", example = "0771234567")
    private String recipientPhone;

    @Schema(description = "Weight in kilograms", example = "2.50")
    private BigDecimal weightKg;

    @Schema(description = "Parcel size", example = "MEDIUM")
    private ParcelSize parcelSize;

    @Schema(description = "Applied pricing tier", example = "STANDARD")
    private QuoteTier appliedTier;

    @Schema(description = "Estimated delivery fee in Sri Lankan Rupees", example = "450.00")
    private BigDecimal estimatedFee;

    @Builder.Default
    @Schema(description = "Currency code", example = "LKR")
    private String currency = "LKR";

    @Schema(description = "Creation timestamp (UTC)", example = "2026-11-07T09:15:00Z")
    private Instant createdAt;
}

