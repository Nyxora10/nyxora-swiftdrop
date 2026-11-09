package com.nyxora.swiftdrop.parcel.pricing.dto;

import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request body for creating a new inter-zone pricing rule.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePricingRuleRequest {

    @NotNull(message = "Origin zone ID is required")
    private Integer originZoneId;

    @NotNull(message = "Destination zone ID is required")
    private Integer destinationZoneId;

    @NotNull(message = "Base fee is required")
    @DecimalMin(value = "0.01", message = "Base fee must be greater than 0")
    private BigDecimal baseFee;

    @NotNull(message = "Rate per kg is required")
    @DecimalMin(value = "0.01", message = "Rate per kg must be greater than 0")
    private BigDecimal ratePerKg;

    @NotNull(message = "Parcel size is required")
    private ParcelSize parcelSize;

    @DecimalMin(value = "1.00", message = "Size multiplier must be at least 1.00")
    @Builder.Default
    private BigDecimal sizeMultiplier = BigDecimal.ONE;
}

