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
 * GoF Strategy Pattern — Concrete Strategy 2: Merchant Corporate Discount Pricing.
 *
 * <p>Applies a <strong>15% contracted corporate discount</strong> on the base fee
 * and rate-per-kg before computing the size multiplier and zone surcharge:
 * <pre>
 *   discountedBaseFee = baseFee × 0.85
 *   discountedRatePerKg = ratePerKg × 0.85
 *   weightCharge = weightKg × discountedRatePerKg
 *   fee = (discountedBaseFee + weightCharge) × sizeMultiplier + destinationZone.baseSurcharge
 * </pre>
 *
 * <p>Applied to parcels booked by approved merchant accounts using Machine-to-Machine OAuth 2.0.
 */
@Component
public class MerchantDiscountPricingStrategy implements PricingStrategy {

    /** 15% corporate discount rate applied to base rates for merchant accounts. */
    private static final BigDecimal MERCHANT_DISCOUNT_RATE = new BigDecimal("0.15");
    private static final BigDecimal ONE = BigDecimal.ONE;

    @Override
    public ParcelQuoteResponse calculateFee(ParcelQuoteRequest request,
                                             PricingRule rule,
                                             DeliveryZone destZone) {

        // Step 1: Apply 15% discount multiplier (retain 85%) to base rates
        BigDecimal discountMultiplier = ONE.subtract(MERCHANT_DISCOUNT_RATE);
        BigDecimal discountedBaseFee = rule.getBaseFee()
                .multiply(discountMultiplier)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountedRatePerKg = rule.getRatePerKg()
                .multiply(discountMultiplier)
                .setScale(2, RoundingMode.HALF_UP);

        // Step 2: Calculate weight charge with discounted rate
        BigDecimal weightCharge = request.getWeightKg()
                .multiply(discountedRatePerKg)
                .setScale(2, RoundingMode.HALF_UP);

        // Step 3: Pre-multiplier subtotal using discounted base fee
        BigDecimal subtotal = discountedBaseFee.add(weightCharge);

        // Step 4: Apply size multiplier
        BigDecimal afterMultiplier = subtotal
                .multiply(rule.getSizeMultiplier())
                .setScale(2, RoundingMode.HALF_UP);

        // Step 5: Add destination zone surcharge (not discounted)
        BigDecimal total = afterMultiplier.add(destZone.getBaseSurcharge())
                .setScale(2, RoundingMode.HALF_UP);

        // Calculate the discount amount for transparent reporting
        BigDecimal standardBase = rule.getBaseFee().add(
                request.getWeightKg().multiply(rule.getRatePerKg()).setScale(2, RoundingMode.HALF_UP));
        BigDecimal discountAmount = standardBase.subtract(discountedBaseFee.add(weightCharge))
                .setScale(2, RoundingMode.HALF_UP);

        return ParcelQuoteResponse.builder()
                .appliedTier(QuoteTier.MERCHANT)
                .baseFee(discountedBaseFee)
                .weightCharge(weightCharge)
                .sizeMultiplier(rule.getSizeMultiplier())
                .zoneSurcharge(destZone.getBaseSurcharge())
                .discountAmount(discountAmount)
                .expressSurcharge(BigDecimal.ZERO)
                .totalFee(total)
                .build();
    }

    @Override
    public boolean supports(QuoteTier tier) {
        return QuoteTier.MERCHANT.equals(tier);
    }
}

