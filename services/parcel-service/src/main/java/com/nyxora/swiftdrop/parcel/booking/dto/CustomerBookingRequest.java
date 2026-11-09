package com.nyxora.swiftdrop.parcel.booking.dto;

import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.pricing.QuoteTier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request payload for booking a parcel delivery via {@code POST /api/v1/parcels/book}.
 * Validated with Jakarta Bean Validation according to Sri Lankan postal and telecom specifications.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Customer Parcel Booking Request Payload")
public class CustomerBookingRequest {

    @NotNull(message = "Origin zone ID is required")
    @Schema(description = "Origin delivery zone ID", example = "1")
    private Integer originZoneId;

    @NotNull(message = "Destination zone ID is required")
    @Schema(description = "Destination delivery zone ID", example = "2")
    private Integer destinationZoneId;

    @NotBlank(message = "Pickup address is required")
    @Size(max = 500, message = "Pickup address cannot exceed 500 characters")
    @Schema(description = "Full pickup address in Sri Lanka", example = "No. 45, Galle Road, Colombo 03")
    private String pickupAddress;

    @NotBlank(message = "Dropoff address is required")
    @Size(max = 500, message = "Dropoff address cannot exceed 500 characters")
    @Schema(description = "Full dropoff/destination address in Sri Lanka", example = "No. 128, Kandy Road, Peradeniya")
    private String dropoffAddress;

    @NotBlank(message = "Recipient name is required")
    @Size(max = 150, message = "Recipient name cannot exceed 150 characters")
    @Schema(description = "Full name of recipient", example = "Kamal Perera")
    private String recipientName;

    @NotBlank(message = "Recipient phone is required")
    @Pattern(
            regexp = "^(\\+94|0)[0-9]{9}$",
            message = "Recipient phone must be a valid Sri Lankan phone number (e.g., 0771234567 or +94771234567)"
    )
    @Schema(description = "Sri Lankan contact number for delivery coordination", example = "0771234567")
    private String recipientPhone;

    @NotNull(message = "Weight is required")
    @DecimalMin(value = "0.01", message = "Weight must be greater than 0")
    @Schema(description = "Weight in kilograms", example = "2.50")
    private BigDecimal weightKg;

    @NotNull(message = "Parcel size is required")
    @Schema(description = "Size classification: SMALL, MEDIUM, or LARGE", example = "MEDIUM")
    private ParcelSize parcelSize;

    @Builder.Default
    @Schema(description = "Delivery pricing tier: STANDARD, EXPRESS, or MERCHANT", example = "STANDARD")
    private QuoteTier tier = QuoteTier.STANDARD;

    @Schema(description = "Optional merchant ID if booking on behalf of an approved merchant", example = "9dc3fc04-5d14-4dd8-b93d-2b19474a49a6")
    private UUID merchantId;
}

