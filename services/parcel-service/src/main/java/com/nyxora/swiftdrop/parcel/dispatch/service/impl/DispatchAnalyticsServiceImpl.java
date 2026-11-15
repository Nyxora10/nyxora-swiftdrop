package com.nyxora.swiftdrop.parcel.dispatch.service.impl;

import com.nyxora.swiftdrop.parcel.dispatch.dto.DailyDeliveryMetricDto;
import com.nyxora.swiftdrop.parcel.dispatch.dto.FailureReasonMetricDto;
import com.nyxora.swiftdrop.parcel.dispatch.dto.OperationalSummaryResponse;
import com.nyxora.swiftdrop.parcel.dispatch.dto.ZonePerformanceMetricDto;
import com.nyxora.swiftdrop.parcel.dispatch.repository.DispatchParcelRepository;
import com.nyxora.swiftdrop.parcel.dispatch.service.DispatchAnalyticsService;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/**
 * Production implementation of {@link DispatchAnalyticsService}.
 * Computes administrative KPI operational summaries, time-series metrics, failure breakdowns,
 * and zone performance across Sri Lankan delivery zones.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchAnalyticsServiceImpl implements DispatchAnalyticsService {

    private final DispatchParcelRepository dispatchParcelRepository;

    @Override
    @Transactional(readOnly = true)
    public OperationalSummaryResponse getOperationalSummary(LocalDate from, LocalDate to) {
        LocalDate effectiveTo = (to != null) ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate effectiveFrom = (from != null) ? from : effectiveTo.minusDays(29);

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new IllegalArgumentException("'from' date (" + effectiveFrom + ") cannot be after 'to' date (" + effectiveTo + ").");
        }

        Instant startInstant = effectiveFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endInstant = effectiveTo.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant();

        log.info("[DispatchAnalyticsService] Generating operational summary for window: [{} to {}] (UTC)",
                startInstant, endInstant);

        // 1. Overall Status Counts & Success Rate
        List<Object[]> statusCounts = dispatchParcelRepository.countParcelsByStatusBetween(startInstant, endInstant);
        Map<ParcelStatus, Long> countsByStatus = new EnumMap<>(ParcelStatus.class);
        for (Object[] row : statusCounts) {
            if (row != null && row.length >= 2 && row[0] instanceof ParcelStatus status && row[1] instanceof Number count) {
                countsByStatus.put(status, count.longValue());
            }
        }

        long deliveredCount = countsByStatus.getOrDefault(ParcelStatus.DELIVERED, 0L);
        long failedCount = countsByStatus.getOrDefault(ParcelStatus.FAILED, 0L);
        long pendingCount = countsByStatus.getOrDefault(ParcelStatus.CREATED, 0L);
        long inTransitCount = countsByStatus.getOrDefault(ParcelStatus.ASSIGNED, 0L)
                + countsByStatus.getOrDefault(ParcelStatus.PICKED_UP, 0L)
                + countsByStatus.getOrDefault(ParcelStatus.IN_TRANSIT, 0L)
                + countsByStatus.getOrDefault(ParcelStatus.OUT_FOR_DELIVERY, 0L);
        long totalBookings = countsByStatus.values().stream().mapToLong(Long::longValue).sum();

        long completedCount = deliveredCount + failedCount;
        double successRate = completedCount > 0
                ? BigDecimal.valueOf((double) deliveredCount / completedCount * 100.0).setScale(2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        // 2. Average Delivery Duration (earliest DELIVERED status transition per distinct parcel)
        List<Object[]> durationRecords = dispatchParcelRepository.getDeliveredParcelTimestampsBetween(startInstant, endInstant);
        Long avgDeliveryDurationMinutes = null;
        String avgDeliveryDurationFormatted = "N/A";

        if (!durationRecords.isEmpty()) {
            long totalMinutes = 0;
            int validCount = 0;
            for (Object[] row : durationRecords) {
                if (row != null && row.length >= 3 && row[1] instanceof Instant bookedAt && row[2] instanceof Instant deliveredAt) {
                    long durationMinutes = Math.max(0, Duration.between(bookedAt, deliveredAt).toMinutes());
                    totalMinutes += durationMinutes;
                    validCount++;
                }
            }
            if (validCount > 0) {
                long avgMinutes = totalMinutes / validCount;
                avgDeliveryDurationMinutes = avgMinutes;
                long hours = avgMinutes / 60;
                long mins = avgMinutes % 60;
                avgDeliveryDurationFormatted = (hours > 0) ? (hours + "h " + mins + "m") : (mins + "m");
            }
        }

        // 3. Failure Breakdown (terminal failure reason per unique failed parcel to eliminate double counting)
        List<Object[]> failedRows = dispatchParcelRepository.getFailedParcelHistoryBetween(startInstant, endInstant);
        Map<UUID, String> terminalFailureByParcel = new HashMap<>();
        for (Object[] row : failedRows) {
            if (row != null && row.length >= 2 && row[0] instanceof UUID parcelId) {
                String reason = (row[1] instanceof String s && !s.isBlank()) ? s.trim() : "UNSPECIFIED";
                terminalFailureByParcel.putIfAbsent(parcelId, reason);
            }
        }

        Map<String, Long> failureCountsByReason = new HashMap<>();
        for (String reason : terminalFailureByParcel.values()) {
            failureCountsByReason.merge(reason, 1L, Long::sum);
        }

        long totalFailedParcels = terminalFailureByParcel.size();
        List<FailureReasonMetricDto> failureBreakdown = failureCountsByReason.entrySet().stream()
                .map(entry -> {
                    double percentage = totalFailedParcels > 0
                            ? BigDecimal.valueOf((double) entry.getValue() / totalFailedParcels * 100.0).setScale(2, RoundingMode.HALF_UP).doubleValue()
                            : 0.0;
                    return FailureReasonMetricDto.builder()
                            .failureReason(entry.getKey())
                            .count(entry.getValue())
                            .percentage(percentage)
                            .build();
                })
                .sorted(Comparator.comparingLong(FailureReasonMetricDto::count).reversed()
                        .thenComparing(FailureReasonMetricDto::failureReason))
                .toList();

        // 4. Daily Time Series (pre-filling every date in range for charting)
        List<Object[]> dailyRows = dispatchParcelRepository.getDailyParcelStatusBetween(startInstant, endInstant);
        Map<LocalDate, Map<ParcelStatus, Long>> dailyStatusMap = new HashMap<>();
        for (Object[] row : dailyRows) {
            if (row != null && row.length >= 2 && row[0] instanceof Instant createdAt && row[1] instanceof ParcelStatus status) {
                LocalDate date = createdAt.atZone(ZoneOffset.UTC).toLocalDate();
                dailyStatusMap
                        .computeIfAbsent(date, k -> new EnumMap<>(ParcelStatus.class))
                        .merge(status, 1L, Long::sum);
            }
        }

        List<DailyDeliveryMetricDto> dailyMetrics = new ArrayList<>();
        LocalDate currentDay = effectiveFrom;
        while (!currentDay.isAfter(effectiveTo)) {
            Map<ParcelStatus, Long> counts = dailyStatusMap.getOrDefault(currentDay, Collections.emptyMap());
            long dayTotal = counts.values().stream().mapToLong(Long::longValue).sum();
            long dayDelivered = counts.getOrDefault(ParcelStatus.DELIVERED, 0L);
            long dayFailed = counts.getOrDefault(ParcelStatus.FAILED, 0L);
            long dayCompleted = dayDelivered + dayFailed;
            double daySuccessRate = dayCompleted > 0
                    ? BigDecimal.valueOf((double) dayDelivered / dayCompleted * 100.0).setScale(2, RoundingMode.HALF_UP).doubleValue()
                    : 0.0;

            dailyMetrics.add(DailyDeliveryMetricDto.builder()
                    .date(currentDay)
                    .totalBookings(dayTotal)
                    .deliveredCount(dayDelivered)
                    .failedCount(dayFailed)
                    .successRatePercentage(daySuccessRate)
                    .build());

            currentDay = currentDay.plusDays(1);
        }

        // 5. Zone-Wise Performance Breakdown
        List<Object[]> zoneRows = dispatchParcelRepository.getZonePerformanceBetween(startInstant, endInstant);
        List<ZonePerformanceMetricDto> zonePerformance = zoneRows.stream()
                .map(row -> {
                    String code = (row[0] != null) ? row[0].toString() : "UNKNOWN";
                    String name = (row[1] != null) ? row[1].toString() : "Unknown Zone";
                    String district = (row[2] != null) ? row[2].toString() : "Unknown District";
                    long total = (row[3] instanceof Number n) ? n.longValue() : 0L;
                    long delivered = (row[4] instanceof Number n) ? n.longValue() : 0L;
                    double rate = total > 0
                            ? BigDecimal.valueOf((double) delivered / total * 100.0).setScale(2, RoundingMode.HALF_UP).doubleValue()
                            : 0.0;
                    return ZonePerformanceMetricDto.builder()
                            .zoneCode(code)
                            .zoneName(name)
                            .district(district)
                            .totalParcels(total)
                            .deliveredParcels(delivered)
                            .successRatePercentage(rate)
                            .build();
                })
                .sorted(Comparator.comparingLong(ZonePerformanceMetricDto::totalParcels).reversed()
                        .thenComparing(ZonePerformanceMetricDto::zoneCode))
                .toList();

        return OperationalSummaryResponse.builder()
                .fromDate(effectiveFrom)
                .toDate(effectiveTo)
                .totalBookings(totalBookings)
                .deliveredCount(deliveredCount)
                .failedCount(failedCount)
                .inTransitCount(inTransitCount)
                .pendingCount(pendingCount)
                .successRatePercentage(successRate)
                .avgDeliveryDurationMinutes(avgDeliveryDurationMinutes)
                .avgDeliveryDurationFormatted(avgDeliveryDurationFormatted)
                .dailyMetrics(dailyMetrics)
                .failureBreakdown(failureBreakdown)
                .zonePerformance(zonePerformance)
                .build();
    }
}
