package com.nyxora.swiftdrop.parcel.tracking.dto;

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
import java.util.List;

/**
 * Public parcel tracking response payload.
 * Provides live delivery progression while concealing recipient Personally Identifiable
 * Information (PII) according to SwiftDrop security and privacy policies.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Public Delivery Tracking Information with PII Masking")
public class ParcelTrackingResponse {

    @Schema(description = "Linear tracking number", example = "SD-20261107-7K9P2X")
    private String trackingNumber;

    @Schema(description = "Current lifecycle status", example = "IN_TRANSIT")
    private ParcelStatus currentStatus;

    @Schema(description = "Origin zone code", example = "WP-COL-01")
    private String originZoneCode;

    @Schema(description = "Origin zone name", example = "Colombo Central")
    private String originZoneName;

    @Schema(description = "Destination zone code", example = "CP-KAN-01")
    private String destinationZoneCode;

    @Schema(description = "Destination zone name", example = "Kandy Metro")
    private String destinationZoneName;

    @Schema(description = "Masked recipient name protecting consumer privacy", example = "K***l P***a")
    private String maskedRecipientName;

    @Schema(description = "Masked recipient contact number", example = "07*****4567")
    private String maskedRecipientPhone;

    @Schema(description = "Masked destination street address concealing private house numbers", example = "No. ***, Kandy Road, Peradeniya")
    private String maskedDropoffAddress;

    @Schema(description = "Weight in kilograms", example = "2.50")
    private BigDecimal weightKg;

    @Schema(description = "Parcel size category", example = "MEDIUM")
    private ParcelSize parcelSize;

    @Schema(description = "Booking registration timestamp (UTC)", example = "2026-11-07T09:15:00Z")
    private Instant createdAt;

    @Schema(description = "Chronological audit timeline of delivery milestones")
    private List<TrackingTimelineEventDto> timeline;
}

