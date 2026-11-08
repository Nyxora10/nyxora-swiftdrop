package com.nyxora.swiftdrop.parcel.pricing;

/**
 * Discriminator enum for the Pricing Strategy Pattern.
 * The client selects a tier when requesting a parcel quote.
 * The {@link PricingEngine} resolves the correct {@link PricingStrategy}
 * implementation based on this value.
 */
public enum QuoteTier {
    /** Standard Sri Lankan zone-based pricing. Default for all bookings. */
    STANDARD,

    /** 15% contracted corporate discount for approved merchant accounts. */
    MERCHANT,

    /** 1.5x expedited same-day delivery surcharge. */
    EXPRESS
}

