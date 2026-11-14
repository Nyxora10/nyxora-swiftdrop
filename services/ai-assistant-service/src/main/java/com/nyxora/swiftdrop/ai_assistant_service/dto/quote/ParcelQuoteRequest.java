package com.nyxora.swiftdrop.ai_assistant_service.dto.quote;

import java.math.BigDecimal;

/**
 * Request payload sent to the Parcel Service quotation endpoint
 * {@code POST /api/v1/parcels/quote}.
 */
public record ParcelQuoteRequest(
        Integer originZoneId,
        Integer destinationZoneId,
        BigDecimal weightKg,
        String parcelSize,
        String tier
) {}

