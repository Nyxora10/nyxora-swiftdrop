package com.nyxora.swiftdrop.parcel.merchant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "B2B Merchant Order Creation Confirmation Response")
public class MerchantOrderResponse {

    @Schema(description = "Internal unique parcel UUID identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    @JsonProperty("parcelId")
    private UUID parcelId;

    @Schema(description = "Customer-visible tracking number code (SD-YYYYMMDD-XXXXXX)", example = "SD-20261107-A4B7C9")
    @JsonProperty("trackingNumber")
    private String trackingNumber;

    @Schema(description = "Associated Merchant business UUID", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    @JsonProperty("merchantId")
    private UUID merchantId;

    @Schema(description = "Origin zone alphanumeric code", example = "WP-COL-01")
    @JsonProperty("originZoneCode")
    private String originZoneCode;

    @Schema(description = "Destination zone alphanumeric code", example = "CP-KDY-01")
    @JsonProperty("destinationZoneCode")
    private String destinationZoneCode;

    @Schema(description = "Recipient customer full name", example = "Kasun Jayawardena")
    @JsonProperty("recipientName")
    private String recipientName;

    @Schema(description = "Package weight in kilograms", example = "2.5")
    @JsonProperty("weightKg")
    private BigDecimal weightKg;

    @Schema(description = "Parcel size tier category", example = "MEDIUM")
    @JsonProperty("parcelSize")
    private ParcelSize parcelSize;

    @Schema(description = "Final estimated delivery fee after 15% corporate discount (in LKR)", example = "765.00")
    @JsonProperty("estimatedFee")
    private BigDecimal estimatedFee;

    @Schema(description = "Corporate discount amount saved (in LKR)", example = "135.00")
    @JsonProperty("discountAmount")
    private BigDecimal discountAmount;

    @Schema(description = "Initial lifecycle progression status", example = "CREATED")
    @JsonProperty("currentStatus")
    private ParcelStatus currentStatus;

    @Schema(description = "External merchant order reference if provided", example = "ORD-2026-9921A")
    @JsonProperty("merchantOrderReference")
    private String merchantOrderReference;

    @Schema(description = "Timestamp when order was registered in system")
    @JsonProperty("createdAt")
    private Instant createdAt;
}
