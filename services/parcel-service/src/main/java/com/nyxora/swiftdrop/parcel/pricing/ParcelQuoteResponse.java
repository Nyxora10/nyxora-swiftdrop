package com.nyxora.swiftdrop.parcel.pricing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Immutable response produced by the Dynamic Pricing Strategy Engine.
 * Includes a full itemized fee breakdown so the client can present
 * a transparent cost summary before booking.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParcelQuoteResponse {

    /** The pricing tier that was applied. */
    private QuoteTier appliedTier;

    /** Base delivery fee before weight, size, and zone surcharges. */
    private BigDecimal baseFee;

    /** Additional charge calculated as weightKg × ratePerKg. */
    private BigDecimal weightCharge;

    /** Multiplier applied for parcel size (1.00 = SMALL, higher for MEDIUM/LARGE). */
    private BigDecimal sizeMultiplier;

    /** Fixed destination zone surcharge. */
    private BigDecimal zoneSurcharge;

    /**
     * Discount amount (positive value) applied to the pre-discount subtotal.
     * Zero for STANDARD and EXPRESS tiers.
     */
    private BigDecimal discountAmount;

    /**
     * Expedited surcharge applied for EXPRESS tier.
     * Zero for STANDARD and MERCHANT tiers.
     */
    private BigDecimal expressSurcharge;

    /** Final total fee after all adjustments. This is the value stored on the Parcel entity. */
    private BigDecimal totalFee;
}

