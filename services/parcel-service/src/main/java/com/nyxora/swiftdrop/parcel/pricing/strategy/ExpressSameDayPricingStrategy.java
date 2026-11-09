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
 * GoF Strategy Pattern — Concrete Strategy 3: Express Same-Day Delivery Pricing.
 *
 * <p>Applies a <strong>1.5× expedited multiplier</strong> to the standard delivery
 * calculation before adding the zone surcharge:
 * <pre>
 *   weightCharge = weightKg × ratePerKg
 *   standardSubtotal = (baseFee + weightCharge) × sizeMultiplier
 *   expressSurcharge = standardSubtotal × 0.5   (the extra 50%)
 *   fee = standardSubtotal × 1.5 + destinationZone.baseSurcharge
 * </pre>
 *
 * <p>Applied to customers requesting guaranteed same-day delivery.
 */
@Component
public class ExpressSameDayPricingStrategy implements PricingStrategy {

    /** The expedited delivery multiplier applied to the standard fee subtotal. */
    private static final BigDecimal EXPRESS_MULTIPLIER = new BigDecimal("1.5");

    @Override
    public ParcelQuoteResponse calculateFee(ParcelQuoteRequest request,
                                             PricingRule rule,
                                             DeliveryZone destZone) {

        // Step 1: Standard weight charge
        BigDecimal weightCharge = request.getWeightKg()
                .multiply(rule.getRatePerKg())
                .setScale(2, RoundingMode.HALF_UP);

        // Step 2: Standard pre-zone subtotal (baseFee + weightCharge) × sizeMultiplier
        BigDecimal standardSubtotal = rule.getBaseFee()
                .add(weightCharge)
                .multiply(rule.getSizeMultiplier())
                .setScale(2, RoundingMode.HALF_UP);

        // Step 3: Apply 1.5x express multiplier
        BigDecimal expressTotal = standardSubtotal
                .multiply(EXPRESS_MULTIPLIER)
                .setScale(2, RoundingMode.HALF_UP);

        // Step 4: The express surcharge is the extra 50% (for display)
        BigDecimal expressSurcharge = expressTotal.subtract(standardSubtotal)
                .setScale(2, RoundingMode.HALF_UP);

        // Step 5: Add zone surcharge
        BigDecimal total = expressTotal.add(destZone.getBaseSurcharge())
                .setScale(2, RoundingMode.HALF_UP);

        return ParcelQuoteResponse.builder()
                .appliedTier(QuoteTier.EXPRESS)
                .baseFee(rule.getBaseFee())
                .weightCharge(weightCharge)
                .sizeMultiplier(rule.getSizeMultiplier())
                .zoneSurcharge(destZone.getBaseSurcharge())
                .discountAmount(BigDecimal.ZERO)
                .expressSurcharge(expressSurcharge)
                .totalFee(total)
                .build();
    }

    @Override
    public boolean supports(QuoteTier tier) {
        return QuoteTier.EXPRESS.equals(tier);
    }
}

