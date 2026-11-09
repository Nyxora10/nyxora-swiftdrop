package com.nyxora.swiftdrop.parcel.pricing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Publicly accessible delivery zone response DTO.
 * Exposes only safe, customer-facing delivery zone identifiers for quotation and booking.
 * Excludes internal administrative surcharge calculations and status metadata.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Public representation of a Sri Lankan delivery zone for quotation and booking")
public class PublicDeliveryZoneResponse {

    @Schema(description = "Zone ID used in rate quote calculations", example = "1")
    private Integer id;

    @Schema(description = "Standard zone code format (<PROVINCE>-<DISTRICT>-<INDEX>)", example = "WP-COL-01")
    private String zoneCode;

    @Schema(description = "Human-readable zone title", example = "Colombo Central Metro")
    private String zoneName;

    @Schema(description = "Sri Lankan administrative district", example = "Colombo")
    private String district;
}

