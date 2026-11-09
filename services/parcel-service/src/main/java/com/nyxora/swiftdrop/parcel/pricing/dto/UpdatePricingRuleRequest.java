package com.nyxora.swiftdrop.parcel.pricing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request body for updating an existing inter-zone pricing rule.
 * All fields are optional — only non-null values are applied (partial update).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePricingRuleRequest {

    private BigDecimal baseFee;
    private BigDecimal ratePerKg;
    private BigDecimal sizeMultiplier;
}

