package com.nyxora.swiftdrop.parcel.merchant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "B2B Merchant Order Creation Request")
public class MerchantOrderRequest {

    @Schema(description = "Origin delivery zone database ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Origin zone ID is required.")
    @JsonProperty("originZoneId")
    private Integer originZoneId;

    @Schema(description = "Destination delivery zone database ID", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Destination zone ID is required.")
    @JsonProperty("destinationZoneId")
    private Integer destinationZoneId;

    @Schema(description = "Merchant warehouse or physical pickup address", example = "Level 5, World Trade Center, Colombo 01", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Pickup address cannot be blank.")
    @JsonProperty("pickupAddress")
    private String pickupAddress;

    @Schema(description = "Customer recipient delivery destination address", example = "No. 42 Peradeniya Road, Kandy", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Dropoff address cannot be blank.")
    @JsonProperty("dropoffAddress")
    private String dropoffAddress;

    @Schema(description = "Recipient full customer name", example = "Kasun Jayawardena", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Recipient name cannot be blank.")
    @JsonProperty("recipientName")
    private String recipientName;

    @Schema(description = "Recipient primary contact phone number", example = "+94771234567", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Recipient phone cannot be blank.")
    @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Recipient phone must be a valid international or local phone number format.")
    @JsonProperty("recipientPhone")
    private String recipientPhone;

    @Schema(description = "Parcel weight in kilograms (minimum 0.1 kg)", example = "2.5", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Weight in kilograms is required.")
    @DecimalMin(value = "0.1", message = "Minimum package weight must be at least 0.1 kg.")
    @JsonProperty("weightKg")
    private BigDecimal weightKg;

    @Schema(description = "Parcel size tier category", example = "MEDIUM", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Parcel size category is required.")
    @JsonProperty("parcelSize")
    private ParcelSize parcelSize;

    @Schema(description = "Optional external merchant order reference / SKU", example = "ORD-2026-9921A")
    @JsonProperty("merchantOrderReference")
    private String merchantOrderReference;
}
