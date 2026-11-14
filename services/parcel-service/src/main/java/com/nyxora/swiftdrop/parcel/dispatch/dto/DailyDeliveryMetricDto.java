package com.nyxora.swiftdrop.parcel.dispatch.dto;

import lombok.Builder;

import java.time.LocalDate;

/**
 * Metric data point representing daily delivery volume and performance for charts.
 */
@Builder
public record DailyDeliveryMetricDto(
        LocalDate date,
        long totalBookings,
        long deliveredCount,
        long failedCount,
        double successRatePercentage
) {
}
