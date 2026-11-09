package com.nyxora.swiftdrop.parcel.pricing.strategy;

import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.entity.PricingRule;
import com.nyxora.swiftdrop.parcel.pricing.ParcelQuoteRequest;
import com.nyxora.swiftdrop.parcel.pricing.ParcelQuoteResponse;
import com.nyxora.swiftdrop.parcel.pricing.PricingStrategy;
import com.nyxora.swiftdrop.parcel.pricing.QuoteTier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * GoF Strategy Pattern — Concrete Strategy 1: Standard Zone Pricing.
 *
 * <p>Applies the base inter-zone pricing formula:
 * <pre>
 *   fee = (baseFee + (weightKg × ratePerKg)) × sizeMultiplier + destinationZone.baseSurcharge
 * </pre>
 *
 * <p>This is the default strategy applied to all standard customer parcel bookings.
 */
@Component
public class StandardZonePricingStrategy implements PricingStrategy {

    @Override
    public ParcelQuoteResponse calculateFee(ParcelQuoteRequest request,
                                             PricingRule rule,
                                             DeliveryZone destZone) {

        // Step 1: Variable weight charge
        BigDecimal weightCharge = request.getWeightKg()
                .multiply(rule.getRatePerKg())
                .setScale(2, RoundingMode.HALF_UP);

        // Step 2: Pre-multiplier subtotal = baseFee + weightCharge
        BigDecimal subtotal = rule.getBaseFee().add(weightCharge);

        // Step 3: Apply size multiplier
        BigDecimal afterMultiplier = subtotal
                .multiply(rule.getSizeMultiplier())
                .setScale(2, RoundingMode.HALF_UP);

        // Step 4: Add destination zone surcharge
        BigDecimal total = afterMultiplier.add(destZone.getBaseSurcharge())
                .setScale(2, RoundingMode.HALF_UP);

        return ParcelQuoteResponse.builder()
                .appliedTier(QuoteTier.STANDARD)
                .baseFee(rule.getBaseFee())
                .weightCharge(weightCharge)
                .sizeMultiplier(rule.getSizeMultiplier())
                .zoneSurcharge(destZone.getBaseSurcharge())
                .discountAmount(BigDecimal.ZERO)
                .expressSurcharge(BigDecimal.ZERO)
                .totalFee(total)
                .build();
    }

    @Override
    public boolean supports(QuoteTier tier) {
        return QuoteTier.STANDARD.equals(tier);
    }
}

