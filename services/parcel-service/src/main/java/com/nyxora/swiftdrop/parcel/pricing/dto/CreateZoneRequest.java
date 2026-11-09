package com.nyxora.swiftdrop.parcel.pricing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request body for creating a new Sri Lankan delivery zone.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateZoneRequest {

    @NotBlank(message = "Zone code is required")
    @Pattern(regexp = "^[A-Z]{2}-[A-Z]{3}-\\d{2}$", message = "Zone code must follow format: WP-COL-01")
    private String zoneCode;

    @NotBlank(message = "Zone name is required")
    private String zoneName;

    @NotBlank(message = "District is required")
    private String district;

    @NotNull(message = "Base surcharge is required")
    @DecimalMin(value = "0.00", message = "Base surcharge cannot be negative")
    private BigDecimal baseSurcharge;
}

