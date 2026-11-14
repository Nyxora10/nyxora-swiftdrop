package com.nyxora.swiftdrop.parcel.dispatch.dto;

import lombok.Builder;

/**
 * Geographic operational performance metric aggregated by delivery zone.
 */
@Builder
public record ZonePerformanceMetricDto(
        String zoneCode,
        String zoneName,
        String district,
        long totalParcels,
        long deliveredParcels,
        double successRatePercentage
) {
}
