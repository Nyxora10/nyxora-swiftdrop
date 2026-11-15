package com.nyxora.swiftdrop.parcel.pricing.dto;

import com.nyxora.swiftdrop.parcel.domain.entity.DefaultPricingRule;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.enums.RouteType;

import java.math.BigDecimal;

public record DefaultPricingRuleResponse(
        Integer id,
        RouteType routeType,
        ParcelSize parcelSize,
        BigDecimal baseFee,
        BigDecimal ratePerKg,
        BigDecimal sizeMultiplier
) {
    public static DefaultPricingRuleResponse from(DefaultPricingRule rule) {
        return new DefaultPricingRuleResponse(
                rule.getId(),
                rule.getRouteType(),
                rule.getParcelSize(),
                rule.getBaseFee(),
                rule.getRatePerKg(),
                rule.getSizeMultiplier()
        );
    }
}
