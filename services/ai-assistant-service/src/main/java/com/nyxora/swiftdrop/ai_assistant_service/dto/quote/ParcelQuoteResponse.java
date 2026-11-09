package com.nyxora.swiftdrop.ai_assistant_service.dto.quote;

import java.math.BigDecimal;

/**
 * Itemized delivery fee quotation response returned by the Parcel Service
 * from {@code POST /api/v1/parcels/quote}.
 */
public record ParcelQuoteResponse(
        String appliedTier,
        BigDecimal baseFee,
        BigDecimal weightCharge,
        BigDecimal sizeMultiplier,
        BigDecimal zoneSurcharge,
        BigDecimal discountAmount,
        BigDecimal expressSurcharge,
        BigDecimal totalFee
) {}

