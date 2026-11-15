package com.nyxora.swiftdrop.parcel.pricing.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDefaultPricingRuleRequest {

    @DecimalMin(value = "0.01", message = "Base fee must be greater than 0")
    private BigDecimal baseFee;

    @DecimalMin(value = "0.01", message = "Rate per kg must be greater than 0")
    private BigDecimal ratePerKg;

    @DecimalMin(value = "1.00", message = "Size multiplier must be at least 1.00")
    private BigDecimal sizeMultiplier;
}
