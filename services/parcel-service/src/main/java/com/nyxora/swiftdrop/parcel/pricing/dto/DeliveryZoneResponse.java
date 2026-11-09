package com.nyxora.swiftdrop.parcel.pricing.dto;

import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;

import java.math.BigDecimal;

/**
 * Stable API representation of a delivery zone.
 *
 * <p>JPA entities remain internal to the parcel service and are not exposed
 * directly through the REST boundary.</p>
 */
public record DeliveryZoneResponse(
        Integer id,
        String zoneCode,
        String zoneName,
        String district,
        BigDecimal baseSurcharge,
        Boolean isActive
) {

    public static DeliveryZoneResponse from(DeliveryZone zone) {
        return new DeliveryZoneResponse(
                zone.getId(),
                zone.getZoneCode(),
                zone.getZoneName(),
                zone.getDistrict(),
                zone.getBaseSurcharge(),
                zone.getIsActive()
        );
    }
}
