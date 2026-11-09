package com.nyxora.swiftdrop.parcel.pricing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Request body for updating an existing delivery zone.
 * All fields are optional — only non-null values are applied (partial update).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateZoneRequest {

    @Size(max = 100, message = "Zone name must be at most 100 characters")
    private String zoneName;

    @Size(max = 50, message = "District must be at most 50 characters")
    private String district;

    @DecimalMin(value = "0.00", message = "Base surcharge cannot be negative")
    private BigDecimal baseSurcharge;
    private Boolean isActive;
}
