package com.nyxora.swiftdrop.parcel.pricing;

import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Immutable request object passed into the Dynamic Pricing Strategy Engine
 * to calculate a delivery fee quote.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParcelQuoteRequest {

    @NotNull(message = "Origin zone ID is required")
    private Integer originZoneId;

    @NotNull(message = "Destination zone ID is required")
    private Integer destinationZoneId;

    @NotNull(message = "Weight is required")
    @DecimalMin(value = "0.01", message = "Weight must be greater than 0")
    private BigDecimal weightKg;

    @NotNull(message = "Parcel size is required")
    private ParcelSize parcelSize;

    @NotNull(message = "Quote tier is required")
    @Builder.Default
    private QuoteTier tier = QuoteTier.STANDARD;
}

