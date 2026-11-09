package com.nyxora.swiftdrop.parcel.pricing;

import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.entity.PricingRule;

/**
 * GoF Strategy Pattern — Strategy Interface.
 *
 * <p>Each concrete implementation encapsulates a distinct fee calculation algorithm:
 * <ul>
 *   <li>{@link strategy.StandardZonePricingStrategy} — Standard zone-based pricing</li>
 *   <li>{@link strategy.MerchantDiscountPricingStrategy} — 15% corporate merchant discount</li>
 *   <li>{@link strategy.ExpressSameDayPricingStrategy} — 1.5x expedited surcharge</li>
 * </ul>
 *
 * <p>The {@link PricingEngine} injects all implementations and selects the correct one
 * at runtime by calling {@link #supports(QuoteTier)} — demonstrating the Open/Closed Principle.
 */
public interface PricingStrategy {

    /**
     * Calculates the itemized delivery fee quote for the given request.
     *
     * @param request     the parcel quote request containing weight, size, and tier
     * @param rule        the applicable inter-zone pricing rule
     * @param destZone    the destination delivery zone (for base surcharge)
     * @return            a fully itemized {@link ParcelQuoteResponse}
     */
    ParcelQuoteResponse calculateFee(ParcelQuoteRequest request, PricingRule rule, DeliveryZone destZone);

    /**
     * Returns {@code true} if this strategy handles the given pricing tier.
     * Used by {@link PricingEngine} to select the correct strategy at runtime.
     *
     * @param tier the requested pricing tier
     * @return true if this strategy handles the tier
     */
    boolean supports(QuoteTier tier);
}

