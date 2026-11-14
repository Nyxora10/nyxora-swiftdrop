package com.nyxora.swiftdrop.parcel.dispatch.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

/**
 * Aggregated operational summary response for admin KPI dashboard (Tasks 1.10 & 6.4).
 */
@Builder
public record OperationalSummaryResponse(
        LocalDate fromDate,
        LocalDate toDate,
        long totalBookings,
        long deliveredCount,
        long failedCount,
        long inTransitCount,
        long pendingCount,
        double successRatePercentage,
        Long avgDeliveryDurationMinutes,
        String avgDeliveryDurationFormatted,
        List<DailyDeliveryMetricDto> dailyMetrics,
        List<FailureReasonMetricDto> failureBreakdown,
        List<ZonePerformanceMetricDto> zonePerformance
) {
}
