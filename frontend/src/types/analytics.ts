/**
 * SwiftDrop KPI Analytics & Daily Reporting Type Definitions (Member 5: Tasks 1.10 & 6.4)
 * Matches backend DTOs in com.nyxora.swiftdrop.parcel.dispatch.dto.*.
 */

/**
 * Metric data point representing daily delivery volume and performance for charts.
 * Matches com.nyxora.swiftdrop.parcel.dispatch.dto.DailyDeliveryMetricDto.
 */
export interface DailyDeliveryMetricDto {
  date: string;
  totalBookings: number;
  deliveredCount: number;
  failedCount: number;
  successRatePercentage: number;
}

/**
 * Breakdown of delivery failure causes and their proportional distribution.
 * Matches com.nyxora.swiftdrop.parcel.dispatch.dto.FailureReasonMetricDto.
 */
export interface FailureReasonMetricDto {
  failureReason: string;
  count: number;
  percentage: number;
}

/**
 * Geographic operational performance metric aggregated by delivery zone.
 * Matches com.nyxora.swiftdrop.parcel.dispatch.dto.ZonePerformanceMetricDto.
 */
export interface ZonePerformanceMetricDto {
  zoneCode: string;
  zoneName: string;
  district: string;
  totalParcels: number;
  deliveredParcels: number;
  successRatePercentage: number;
}

/**
 * Aggregated operational summary response for admin KPI dashboard.
 * Matches com.nyxora.swiftdrop.parcel.dispatch.dto.OperationalSummaryResponse.
 */
export interface OperationalSummaryResponse {
  fromDate: string;
  toDate: string;
  totalBookings: number;
  deliveredCount: number;
  failedCount: number;
  inTransitCount: number;
  pendingCount: number;
  successRatePercentage: number;
  avgDeliveryDurationMinutes: number | null;
  avgDeliveryDurationFormatted: string | null;
  dailyMetrics: DailyDeliveryMetricDto[];
  failureBreakdown: FailureReasonMetricDto[];
  zonePerformance: ZonePerformanceMetricDto[];
}

