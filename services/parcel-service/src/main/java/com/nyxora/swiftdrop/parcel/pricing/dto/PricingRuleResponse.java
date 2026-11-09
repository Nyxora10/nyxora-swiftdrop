package com.nyxora.swiftdrop.parcel.pricing.dto;

import com.nyxora.swiftdrop.parcel.domain.entity.PricingRule;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;

import java.math.BigDecimal;

/** Stable API representation of an inter-zone pricing rule. */
public record PricingRuleResponse(
        Integer id,
        DeliveryZoneResponse originZone,
        DeliveryZoneResponse destinationZone,
        BigDecimal baseFee,
        BigDecimal ratePerKg,
        ParcelSize parcelSize,
        BigDecimal sizeMultiplier
) {

    public static PricingRuleResponse from(PricingRule rule) {
        return new PricingRuleResponse(
                rule.getId(),
                rule.getOriginZone() == null ? null : DeliveryZoneResponse.from(rule.getOriginZone()),
                rule.getDestinationZone() == null ? null : DeliveryZoneResponse.from(rule.getDestinationZone()),
                rule.getBaseFee(),
                rule.getRatePerKg(),
                rule.getParcelSize(),
                rule.getSizeMultiplier()
        );
    }
}
