package com.nyxora.swiftdrop.parcel.dispatch.service.impl;

import com.nyxora.swiftdrop.parcel.dispatch.dto.DailyDeliveryMetricDto;
import com.nyxora.swiftdrop.parcel.dispatch.dto.FailureReasonMetricDto;
import com.nyxora.swiftdrop.parcel.dispatch.dto.OperationalSummaryResponse;
import com.nyxora.swiftdrop.parcel.dispatch.dto.ZonePerformanceMetricDto;
import com.nyxora.swiftdrop.parcel.dispatch.repository.DispatchParcelRepository;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DispatchAnalyticsServiceImpl Unit Tests (Member 5: Tasks 1.10 & 6.4)")
class DispatchAnalyticsServiceImplTest {

    @Mock
    private DispatchParcelRepository dispatchParcelRepository;

    @InjectMocks
    private DispatchAnalyticsServiceImpl analyticsService;

    @Test
    @DisplayName("getOperationalSummary with explicit date range computes all KPIs, daily series, and breakdowns")
    void getOperationalSummary_withExplicitDates_computesMetricsSuccessfully() {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 3);

        Instant day1 = from.atStartOfDay(ZoneOffset.UTC).toInstant().plus(Duration.ofHours(2));
        Instant day2 = from.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().plus(Duration.ofHours(3));
        Instant day3 = to.atStartOfDay(ZoneOffset.UTC).toInstant().plus(Duration.ofHours(4));

        // 1. Status counts
        when(dispatchParcelRepository.countParcelsByStatusBetween(any(), any()))
                .thenReturn(List.of(
                        new Object[]{ParcelStatus.DELIVERED, 4L},
                        new Object[]{ParcelStatus.FAILED, 1L},
                        new Object[]{ParcelStatus.IN_TRANSIT, 2L},
                        new Object[]{ParcelStatus.CREATED, 1L}
                ));

        // 2. Durations (4 delivered parcels, avg 150 min = 2h 30m)
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        when(dispatchParcelRepository.getDeliveredParcelTimestampsBetween(any(), any()))
                .thenReturn(List.of(
                        new Object[]{p1, day1, day1.plus(Duration.ofMinutes(120))}, // 120m
                        new Object[]{p2, day2, day2.plus(Duration.ofMinutes(180))}  // 180m -> avg 150m
                ));

        // 3. Failure breakdown with deduplication
        UUID failedParcel1 = UUID.randomUUID();
        UUID failedParcel2 = UUID.randomUUID();
        when(dispatchParcelRepository.getFailedParcelHistoryBetween(any(), any()))
                .thenReturn(List.of(
                        // failedParcel1 had two attempts; latest reason is RECIPIENT_UNAVAILABLE
                        new Object[]{failedParcel1, "RECIPIENT_UNAVAILABLE", day3.plus(Duration.ofMinutes(10))},
                        new Object[]{failedParcel1, "CUSTOMER_BUSY", day3},
                        new Object[]{failedParcel2, "INCORRECT_ADDRESS", day2}
                ));

        // 4. Daily series
        when(dispatchParcelRepository.getDailyParcelStatusBetween(any(), any()))
                .thenReturn(List.of(
                        new Object[]{day1, ParcelStatus.DELIVERED},
                        new Object[]{day1, ParcelStatus.CREATED},
                        new Object[]{day2, ParcelStatus.DELIVERED},
                        new Object[]{day2, ParcelStatus.FAILED},
                        new Object[]{day3, ParcelStatus.IN_TRANSIT}
                ));

        // 5. Zone performance
        when(dispatchParcelRepository.getZonePerformanceBetween(any(), any()))
                .thenReturn(List.of(
                        new Object[]{"WP-COL-01", "Colombo Central", "Colombo", 5L, 4L},
                        new Object[]{"CP-KAN-01", "Kandy Town", "Kandy", 3L, 0L}
                ));

        OperationalSummaryResponse response = analyticsService.getOperationalSummary(from, to);

        assertThat(response).isNotNull();
        assertThat(response.fromDate()).isEqualTo(from);
        assertThat(response.toDate()).isEqualTo(to);

        // Counts
        assertThat(response.totalBookings()).isEqualTo(8L);
        assertThat(response.deliveredCount()).isEqualTo(4L);
        assertThat(response.failedCount()).isEqualTo(1L);
        assertThat(response.inTransitCount()).isEqualTo(2L);
        assertThat(response.pendingCount()).isEqualTo(1L);

        // Success rate: 4 / (4 + 1) = 80.0%
        assertThat(response.successRatePercentage()).isEqualTo(80.0);

        // Average duration: (120 + 180) / 2 = 150m -> "2h 30m"
        assertThat(response.avgDeliveryDurationMinutes()).isEqualTo(150L);
        assertThat(response.avgDeliveryDurationFormatted()).isEqualTo("2h 30m");

        // Failure breakdown: deduplicated to 2 distinct failed parcels
        assertThat(response.failureBreakdown()).hasSize(2);
        FailureReasonMetricDto topFailure = response.failureBreakdown().get(0);
        assertThat(topFailure.count()).isEqualTo(1L);
        assertThat(topFailure.percentage()).isEqualTo(50.0);

        // Daily metrics: Exactly 3 days (Nov 1, Nov 2, Nov 3)
        assertThat(response.dailyMetrics()).hasSize(3);
        DailyDeliveryMetricDto day1Metric = response.dailyMetrics().get(0);
        assertThat(day1Metric.date()).isEqualTo(from);
        assertThat(day1Metric.totalBookings()).isEqualTo(2L);
        assertThat(day1Metric.deliveredCount()).isEqualTo(1L);
        assertThat(day1Metric.failedCount()).isEqualTo(0L);
        assertThat(day1Metric.successRatePercentage()).isEqualTo(100.0);

        // Zone performance
        assertThat(response.zonePerformance()).hasSize(2);
        ZonePerformanceMetricDto colomboZone = response.zonePerformance().get(0);
        assertThat(colomboZone.zoneCode()).isEqualTo("WP-COL-01");
        assertThat(colomboZone.totalParcels()).isEqualTo(5L);
        assertThat(colomboZone.deliveredParcels()).isEqualTo(4L);
        assertThat(colomboZone.successRatePercentage()).isEqualTo(80.0);

        verify(dispatchParcelRepository).countParcelsByStatusBetween(any(), any());
        verify(dispatchParcelRepository).getDeliveredParcelTimestampsBetween(any(), any());
        verify(dispatchParcelRepository).getFailedParcelHistoryBetween(any(), any());
        verify(dispatchParcelRepository).getDailyParcelStatusBetween(any(), any());
        verify(dispatchParcelRepository).getZonePerformanceBetween(any(), any());
    }

    @Test
    @DisplayName("getOperationalSummary with null dates defaults to 30-day window ending today")
    void getOperationalSummary_withNullDates_usesDefault30DayWindow() {
        when(dispatchParcelRepository.countParcelsByStatusBetween(any(), any())).thenReturn(Collections.emptyList());
        when(dispatchParcelRepository.getDeliveredParcelTimestampsBetween(any(), any())).thenReturn(Collections.emptyList());
        when(dispatchParcelRepository.getFailedParcelHistoryBetween(any(), any())).thenReturn(Collections.emptyList());
        when(dispatchParcelRepository.getDailyParcelStatusBetween(any(), any())).thenReturn(Collections.emptyList());
        when(dispatchParcelRepository.getZonePerformanceBetween(any(), any())).thenReturn(Collections.emptyList());

        OperationalSummaryResponse response = analyticsService.getOperationalSummary(null, null);

        assertThat(response).isNotNull();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        assertThat(response.toDate()).isEqualTo(today);
        assertThat(response.fromDate()).isEqualTo(today.minusDays(29));
        assertThat(response.dailyMetrics()).hasSize(30);
    }

    @Test
    @DisplayName("getOperationalSummary when from date is chronologically after to date throws IllegalArgumentException")
    void getOperationalSummary_whenFromAfterTo_throwsIllegalArgumentException() {
        LocalDate from = LocalDate.of(2026, 11, 10);
        LocalDate to = LocalDate.of(2026, 11, 5);

        assertThatThrownBy(() -> analyticsService.getOperationalSummary(from, to))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be after");
    }

    @Test
    @DisplayName("getOperationalSummary when no records exist returns zero counters and N/A formatted duration")
    void getOperationalSummary_whenNoParcelsExist_returnsZeroCounters() {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 2);

        when(dispatchParcelRepository.countParcelsByStatusBetween(any(), any())).thenReturn(Collections.emptyList());
        when(dispatchParcelRepository.getDeliveredParcelTimestampsBetween(any(), any())).thenReturn(Collections.emptyList());
        when(dispatchParcelRepository.getFailedParcelHistoryBetween(any(), any())).thenReturn(Collections.emptyList());
        when(dispatchParcelRepository.getDailyParcelStatusBetween(any(), any())).thenReturn(Collections.emptyList());
        when(dispatchParcelRepository.getZonePerformanceBetween(any(), any())).thenReturn(Collections.emptyList());

        OperationalSummaryResponse response = analyticsService.getOperationalSummary(from, to);

        assertThat(response.totalBookings()).isEqualTo(0L);
        assertThat(response.deliveredCount()).isEqualTo(0L);
        assertThat(response.failedCount()).isEqualTo(0L);
        assertThat(response.inTransitCount()).isEqualTo(0L);
        assertThat(response.pendingCount()).isEqualTo(0L);
        assertThat(response.successRatePercentage()).isEqualTo(0.0);
        assertThat(response.avgDeliveryDurationMinutes()).isNull();
        assertThat(response.avgDeliveryDurationFormatted()).isEqualTo("N/A");
        assertThat(response.failureBreakdown()).isEmpty();
        assertThat(response.zonePerformance()).isEmpty();
        assertThat(response.dailyMetrics()).hasSize(2);
        assertThat(response.dailyMetrics().get(0).totalBookings()).isEqualTo(0L);
        assertThat(response.dailyMetrics().get(0).successRatePercentage()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("getOperationalSummary formats short durations as minutes only")
    void getOperationalSummary_formatsShortDurationsAsMinutesOnly() {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 1);
        Instant now = Instant.now();

        when(dispatchParcelRepository.countParcelsByStatusBetween(any(), any())).thenReturn(Collections.emptyList());
        when(dispatchParcelRepository.getDeliveredParcelTimestampsBetween(any(), any()))
                .thenReturn(Collections.singletonList(
                        new Object[]{UUID.randomUUID(), now, now.plus(Duration.ofMinutes(45))}
                ));
        when(dispatchParcelRepository.getFailedParcelHistoryBetween(any(), any())).thenReturn(Collections.emptyList());
        when(dispatchParcelRepository.getDailyParcelStatusBetween(any(), any())).thenReturn(Collections.emptyList());
        when(dispatchParcelRepository.getZonePerformanceBetween(any(), any())).thenReturn(Collections.emptyList());

        OperationalSummaryResponse response = analyticsService.getOperationalSummary(from, to);

        assertThat(response.avgDeliveryDurationMinutes()).isEqualTo(45L);
        assertThat(response.avgDeliveryDurationFormatted()).isEqualTo("45m");
    }

    @Test
    @DisplayName("getOperationalSummary handles blank failure reason and null zone attributes gracefully")
    void getOperationalSummary_handlesBlankFailureAndNullZoneFields() {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 1);

        when(dispatchParcelRepository.countParcelsByStatusBetween(any(), any())).thenReturn(Collections.emptyList());
        when(dispatchParcelRepository.getDeliveredParcelTimestampsBetween(any(), any())).thenReturn(Collections.emptyList());
        when(dispatchParcelRepository.getFailedParcelHistoryBetween(any(), any()))
                .thenReturn(Collections.singletonList(
                        new Object[]{UUID.randomUUID(), "   ", Instant.now()}
                ));
        when(dispatchParcelRepository.getDailyParcelStatusBetween(any(), any())).thenReturn(Collections.emptyList());
        when(dispatchParcelRepository.getZonePerformanceBetween(any(), any()))
                .thenReturn(Collections.singletonList(
                        new Object[]{null, null, null, 10L, 5L}
                ));

        OperationalSummaryResponse response = analyticsService.getOperationalSummary(from, to);

        assertThat(response.failureBreakdown()).hasSize(1);
        assertThat(response.failureBreakdown().get(0).failureReason()).isEqualTo("UNSPECIFIED");

        assertThat(response.zonePerformance()).hasSize(1);
        ZonePerformanceMetricDto zone = response.zonePerformance().get(0);
        assertThat(zone.zoneCode()).isEqualTo("UNKNOWN");
        assertThat(zone.zoneName()).isEqualTo("Unknown Zone");
        assertThat(zone.district()).isEqualTo("Unknown District");
        assertThat(zone.successRatePercentage()).isEqualTo(50.0);
    }
}
