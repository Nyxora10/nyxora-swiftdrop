package com.nyxora.swiftdrop.parcel.dispatch.service;

import com.nyxora.swiftdrop.parcel.dispatch.dto.OperationalSummaryResponse;

import java.time.LocalDate;

/**
 * Service interface for Member 5's Admin Operational KPI Analytics and Daily Reports (Tasks 1.10 & 6.4).
 */
public interface DispatchAnalyticsService {

    /**
     * Computes the system-wide operational delivery metrics and time series for a specified date window.
     *
     * @param from start date (inclusive); if null, defaults to 30 days prior to {@code to}
     * @param to   end date (inclusive); if null, defaults to current date in UTC
     * @return aggregated operational summary report
     * @throws IllegalArgumentException if {@code from} is chronologically after {@code to}
     */
    OperationalSummaryResponse getOperationalSummary(LocalDate from, LocalDate to);
}
