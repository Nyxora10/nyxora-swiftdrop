'use client';

import React, { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import axios from 'axios';
import AppShell from '@/components/layout/AppShell';
import Button from '@/components/ui/Button';
import Spinner from '@/components/ui/Spinner';
import { useToast } from '@/components/layout/ToastContext';
import {
  BarChart3,
  TrendingUp,
  AlertTriangle,
  Calendar,
  RefreshCw,
  X,
  AlertCircle,
  MapPin,
  Layers,
  CheckCircle,
} from 'lucide-react';
import { getOperationalSummary } from '@/services/analyticsService';
import {
  OperationalSummaryResponse,
  DailyDeliveryMetricDto,
} from '@/types/analytics';

function readApiError(error: unknown, fallback: string): string {
  if (axios.isAxiosError(error)) {
    const payload = error.response?.data as {
      detail?: string;
      message?: string;
      title?: string;
    } | undefined;
    return payload?.detail || payload?.message || payload?.title || fallback;
  }
  return error instanceof Error ? error.message : fallback;
}

function formatIsoDate(d: Date): string {
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function formatReasonLabel(reason: string): string {
  return reason
    .toLowerCase()
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

type DatePreset = 'today' | '7days' | '30days' | 'monthToDate' | 'custom';

export default function AdminAnalyticsPage() {
  const toast = useToast();

  // Date Filter State
  const [activePreset, setActivePreset] = useState<DatePreset>('30days');
  const [startDate, setStartDate] = useState<string>('');
  const [endDate, setEndDate] = useState<string>('');
  const [dateError, setDateError] = useState<string | null>(null);

  // Initialize 30-day default date window on client mount
  useEffect(() => {
    const now = new Date();
    const todayStr = formatIsoDate(now);
    const past = new Date();
    past.setDate(past.getDate() - 30);
    setStartDate(formatIsoDate(past));
    setEndDate(todayStr);
  }, []);

  // Data & Loading States
  const [summary, setSummary] = useState<OperationalSummaryResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);

  // Interactive Chart Tooltip
  const [hoveredPoint, setHoveredPoint] = useState<DailyDeliveryMetricDto | null>(null);

  // Stale request guard
  const loadSeqRef = useRef(0);

  // Preset Handlers
  const applyPreset = (preset: DatePreset) => {
    setActivePreset(preset);
    setDateError(null);

    const now = new Date();
    const todayStr = formatIsoDate(now);

    switch (preset) {
      case 'today':
        setStartDate(todayStr);
        setEndDate(todayStr);
        break;
      case '7days': {
        const d = new Date();
        d.setDate(d.getDate() - 7);
        setStartDate(formatIsoDate(d));
        setEndDate(todayStr);
        break;
      }
      case '30days': {
        const d = new Date();
        d.setDate(d.getDate() - 30);
        setStartDate(formatIsoDate(d));
        setEndDate(todayStr);
        break;
      }
      case 'monthToDate': {
        const d = new Date(now.getFullYear(), now.getMonth(), 1);
        setStartDate(formatIsoDate(d));
        setEndDate(todayStr);
        break;
      }
      case 'custom':
        // Keep existing custom inputs
        break;
    }
  };

  // Fetch Summary Data
  const fetchAnalytics = useCallback(
    async (isRefresh = false) => {
      if (!startDate || !endDate) return;

      // Validate dates
      if (startDate > endDate) {
        setDateError('Start date cannot be after end date.');
        return;
      }
      setDateError(null);

      const seq = ++loadSeqRef.current;
      if (isRefresh) {
        setIsRefreshing(true);
      } else {
        setIsLoading(true);
      }
      setPageError(null);

      try {
        const data = await getOperationalSummary(startDate, endDate);
        if (seq !== loadSeqRef.current) return;
        setSummary(data);
      } catch (err) {
        if (seq !== loadSeqRef.current) return;
        const msg = readApiError(err, 'Failed to retrieve operational KPI analytics.');
        setPageError(msg);
        toast.error(msg);
      } finally {
        if (seq === loadSeqRef.current) {
          setIsLoading(false);
          setIsRefreshing(false);
        }
      }
    },
    [startDate, endDate, toast]
  );

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- this effect synchronizes remote analytics data into the page state.
    void fetchAnalytics();
  }, [fetchAnalytics]);

  // Chart Coordinates Calculation for Daily Metrics
  const chartData = useMemo(() => {
    if (!summary?.dailyMetrics || summary.dailyMetrics.length === 0) return null;

    const metrics = summary.dailyMetrics;
    const maxVal = Math.max(
      ...metrics.map((m) => Math.max(m.totalBookings, m.deliveredCount, m.failedCount)),
      5
    );

    const width = 800;
    const height = 260;
    const paddingX = 40;
    const paddingY = 30;

    const plotWidth = width - paddingX * 2;
    const plotHeight = height - paddingY * 2;

    const points = metrics.map((m, idx) => {
      const x =
        metrics.length === 1
          ? width / 2
          : paddingX + (idx / (metrics.length - 1)) * plotWidth;
      const yBookings = height - paddingY - (m.totalBookings / maxVal) * plotHeight;
      const yDelivered = height - paddingY - (m.deliveredCount / maxVal) * plotHeight;
      const yFailed = height - paddingY - (m.failedCount / maxVal) * plotHeight;

      return {
        metric: m,
        x,
        yBookings,
        yDelivered,
        yFailed,
      };
    });

    const bookingsPath = points.map((p, idx) => `${idx === 0 ? 'M' : 'L'} ${p.x} ${p.yBookings}`).join(' ');
    const deliveredPath = points.map((p, idx) => `${idx === 0 ? 'M' : 'L'} ${p.x} ${p.yDelivered}`).join(' ');
    const failedPath = points.map((p, idx) => `${idx === 0 ? 'M' : 'L'} ${p.x} ${p.yFailed}`).join(' ');

    const areaPath = `${bookingsPath} L ${points[points.length - 1].x} ${height - paddingY} L ${points[0].x} ${
      height - paddingY
    } Z`;

    return {
      width,
      height,
      paddingX,
      paddingY,
      maxVal,
      points,
      bookingsPath,
      deliveredPath,
      failedPath,
      areaPath,
    };
  }, [summary?.dailyMetrics]);

  // Color mapping for failure breakdown segments
  const failureColors = [
    'bg-rose-500 text-rose-500 border-rose-300',
    'bg-amber-500 text-amber-500 border-amber-300',
    'bg-sky-500 text-sky-500 border-sky-300',
    'bg-indigo-500 text-indigo-500 border-indigo-300',
    'bg-purple-500 text-purple-500 border-purple-300',
    'bg-slate-500 text-slate-500 border-slate-300',
  ];

  return (
    <AppShell>
      <div className="space-y-6">
        {/* Error Notification Banner */}
        {pageError && (
          <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <AlertCircle className="h-4 w-4 shrink-0 text-red-600" />
              <span>{pageError}</span>
            </div>
            <button
              onClick={() => setPageError(null)}
              className="p-1 opacity-70 hover:opacity-100 transition-opacity cursor-pointer"
              aria-label="Dismiss error banner"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        )}

        {/* Dashboard Top Header */}
        <div className="pb-4 border-b border-slate-200">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div>
              <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">
                Operations &amp; KPI Analytics
              </h1>
              <p className="text-sm text-slate-500 mt-1">
                Monitor delivery throughput, failure distributions, duration metrics, and geographic hub performance.
              </p>
            </div>

            <div className="flex items-center gap-2.5">
              <Button
                variant="secondary"
                size="sm"
                onClick={() => void fetchAnalytics(true)}
                disabled={isRefreshing || isLoading}
                leftIcon={<RefreshCw className={`w-3.5 h-3.5 ${isRefreshing ? 'animate-spin' : ''}`} />}
              >
                {isRefreshing ? 'Refreshing...' : 'Refresh Metrics'}
              </Button>
            </div>
          </div>
        </div>

        {/* Date Filtering Toolbar */}
        <div className="rounded-2xl border border-slate-200/80 bg-white p-4 shadow-2xs space-y-3">
          <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-3">
            {/* Quick Presets */}
            <div className="flex flex-wrap items-center gap-1.5">
              <span className="text-xs font-bold text-slate-500 uppercase tracking-wider mr-1.5 flex items-center gap-1">
                <Calendar className="w-3.5 h-3.5 text-blue-600" />
                Date Range:
              </span>
              {(
                [
                  { id: 'today', label: 'Today' },
                  { id: '7days', label: 'Last 7 Days' },
                  { id: '30days', label: 'Last 30 Days' },
                  { id: 'monthToDate', label: 'Month to Date' },
                  { id: 'custom', label: 'Custom' },
                ] as const
              ).map((preset) => (
                <button
                  key={preset.id}
                  onClick={() => applyPreset(preset.id)}
                  className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-all cursor-pointer ${
                    activePreset === preset.id
                      ? 'bg-blue-600 text-white shadow-xs shadow-blue-500/20'
                      : 'bg-slate-100 text-slate-600 hover:bg-slate-200/70 hover:text-slate-900'
                  }`}
                >
                  {preset.label}
                </button>
              ))}
            </div>

            {/* Custom Date Pickers */}
            <div className="flex flex-wrap items-center gap-2 text-xs">
              <div className="flex items-center gap-1.5">
                <span className="text-slate-500 font-medium">From:</span>
                <input
                  type="date"
                  value={startDate}
                  onChange={(e) => {
                    setActivePreset('custom');
                    setStartDate(e.target.value);
                  }}
                  className="py-1.5 px-2.5 rounded-xl border border-slate-200 bg-slate-50/50 text-xs font-medium text-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 focus:bg-white transition-all cursor-pointer"
                />
              </div>

              <div className="flex items-center gap-1.5">
                <span className="text-slate-500 font-medium">To:</span>
                <input
                  type="date"
                  value={endDate}
                  onChange={(e) => {
                    setActivePreset('custom');
                    setEndDate(e.target.value);
                  }}
                  className="py-1.5 px-2.5 rounded-xl border border-slate-200 bg-slate-50/50 text-xs font-medium text-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 focus:bg-white transition-all cursor-pointer"
                />
              </div>
            </div>
          </div>

          {/* Date Validation Alert */}
          {dateError && (
            <p className="text-xs text-rose-600 font-semibold flex items-center gap-1">
              <AlertCircle className="w-3.5 h-3.5 shrink-0" />
              <span>{dateError}</span>
            </p>
          )}
        </div>

        {/* 7 KPI Summary Cards Grid */}
        <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-7 gap-3.5">
          {/* Card 1: Total Bookings */}
          <div className="rounded-2xl border border-slate-200/80 bg-white p-4 shadow-2xs">
            <p className="text-[11px] font-bold uppercase tracking-wider text-slate-400">Total Bookings</p>
            <div className="mt-2 flex items-baseline gap-1">
              <span className="text-2xl font-extrabold text-slate-900 tracking-tight">
                {isLoading ? '...' : summary?.totalBookings ?? 0}
              </span>
            </div>
            <p className="mt-2 text-[10px] text-slate-400 truncate">Orders placed in window</p>
          </div>

          {/* Card 2: Delivered */}
          <div className="rounded-2xl border border-emerald-200/70 bg-emerald-50/30 p-4 shadow-2xs">
            <p className="text-[11px] font-bold uppercase tracking-wider text-emerald-700">Delivered</p>
            <div className="mt-2 flex items-baseline gap-1">
              <span className="text-2xl font-extrabold text-emerald-700 tracking-tight">
                {isLoading ? '...' : summary?.deliveredCount ?? 0}
              </span>
            </div>
            <p className="mt-2 text-[10px] text-emerald-600 truncate">Successful handoffs</p>
          </div>

          {/* Card 3: Failed */}
          <div className="rounded-2xl border border-rose-200/70 bg-rose-50/30 p-4 shadow-2xs">
            <p className="text-[11px] font-bold uppercase tracking-wider text-rose-700">Failed</p>
            <div className="mt-2 flex items-baseline gap-1">
              <span className="text-2xl font-extrabold text-rose-700 tracking-tight">
                {isLoading ? '...' : summary?.failedCount ?? 0}
              </span>
            </div>
            <p className="mt-2 text-[10px] text-rose-600 truncate">Fulfillment exceptions</p>
          </div>

          {/* Card 4: In-Transit */}
          <div className="rounded-2xl border border-blue-200/70 bg-blue-50/30 p-4 shadow-2xs">
            <p className="text-[11px] font-bold uppercase tracking-wider text-blue-700">In Transit</p>
            <div className="mt-2 flex items-baseline gap-1">
              <span className="text-2xl font-extrabold text-blue-700 tracking-tight">
                {isLoading ? '...' : summary?.inTransitCount ?? 0}
              </span>
            </div>
            <p className="mt-2 text-[10px] text-blue-600 truncate">Out for delivery now</p>
          </div>

          {/* Card 5: Pending Dispatch */}
          <div className="rounded-2xl border border-amber-200/70 bg-amber-50/30 p-4 shadow-2xs">
            <p className="text-[11px] font-bold uppercase tracking-wider text-amber-700">Pending</p>
            <div className="mt-2 flex items-baseline gap-1">
              <span className="text-2xl font-extrabold text-amber-700 tracking-tight">
                {isLoading ? '...' : summary?.pendingCount ?? 0}
              </span>
            </div>
            <p className="mt-2 text-[10px] text-amber-600 truncate">Awaiting courier</p>
          </div>

          {/* Card 6: Success Rate */}
          <div className="rounded-2xl border border-slate-200/80 bg-white p-4 shadow-2xs">
            <p className="text-[11px] font-bold uppercase tracking-wider text-slate-400">Success Rate</p>
            <div className="mt-2 flex items-baseline gap-1">
              <span
                className={`text-2xl font-extrabold tracking-tight ${
                  (summary?.successRatePercentage ?? 0) >= 85
                    ? 'text-emerald-600'
                    : (summary?.successRatePercentage ?? 0) >= 70
                    ? 'text-amber-600'
                    : 'text-rose-600'
                }`}
              >
                {isLoading ? '...' : `${Number(summary?.successRatePercentage ?? 0).toFixed(1)}%`}
              </span>
            </div>
            <p className="mt-2 text-[10px] text-slate-400 truncate">Resolved orders ratio</p>
          </div>

          {/* Card 7: Avg Duration */}
          <div className="rounded-2xl border border-slate-200/80 bg-white p-4 shadow-2xs">
            <p className="text-[11px] font-bold uppercase tracking-wider text-slate-400">Avg Duration</p>
            <div className="mt-2 flex items-baseline gap-1">
              <span className="text-xl font-extrabold text-slate-900 tracking-tight truncate">
                {isLoading
                  ? '...'
                  : summary?.avgDeliveryDurationFormatted ||
                    (summary?.avgDeliveryDurationMinutes ? `${summary.avgDeliveryDurationMinutes}m` : 'N/A')}
              </span>
            </div>
            <p className="mt-2 text-[10px] text-slate-400 truncate">
              {summary?.avgDeliveryDurationMinutes ? `${summary.avgDeliveryDurationMinutes} min avg` : 'No deliveries'}
            </p>
          </div>
        </div>

        {/* Daily Delivery Analytics Chart */}
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-2xs">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between pb-4 border-b border-slate-100 gap-3">
            <div>
              <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
                <TrendingUp className="w-4 h-4 text-blue-600" />
                Daily Delivery Throughput &amp; Volume Trends
              </h3>
              <p className="text-xs text-slate-500 mt-0.5">
                Time-series comparison of daily bookings, successful deliveries, and exceptions
              </p>
            </div>

            {/* Chart Legend */}
            <div className="flex items-center gap-4 text-xs font-semibold">
              <div className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-full bg-blue-600" />
                <span className="text-slate-600">Total Bookings</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-full bg-emerald-500" />
                <span className="text-slate-600">Delivered</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-full bg-rose-500" />
                <span className="text-slate-600">Failed</span>
              </div>
            </div>
          </div>

          {/* Chart Canvas */}
          <div className="pt-6">
            {isLoading ? (
              <div className="py-24 flex flex-col items-center justify-center text-slate-400 text-xs">
                <Spinner size="lg" />
                <span className="mt-3 font-medium">Aggregating time series metrics...</span>
              </div>
            ) : !chartData || chartData.points.length === 0 ? (
              <div className="py-20 text-center text-slate-400 text-xs">
                <BarChart3 className="w-8 h-8 mx-auto mb-2 opacity-50" />
                <p className="font-semibold text-slate-600">No time series activity recorded</p>
                <p className="mt-1">Try expanding the date range filter above.</p>
              </div>
            ) : (
              <div className="relative">
                {/* SVG Visualizer */}
                <div className="w-full overflow-x-auto">
                  <svg
                    viewBox={`0 0 ${chartData.width} ${chartData.height}`}
                    className="w-full h-64 overflow-visible"
                  >
                    <defs>
                      <linearGradient id="bookingsGradient" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="#3b82f6" stopOpacity="0.2" />
                        <stop offset="100%" stopColor="#3b82f6" stopOpacity="0.0" />
                      </linearGradient>
                    </defs>

                    {/* Horizontal Gridlines */}
                    {[0, 0.25, 0.5, 0.75, 1].map((pct) => {
                      const y =
                        chartData.height -
                        chartData.paddingY -
                        pct * (chartData.height - chartData.paddingY * 2);
                      const labelVal = Math.round(pct * chartData.maxVal);
                      return (
                        <g key={pct}>
                          <line
                            x1={chartData.paddingX}
                            y1={y}
                            x2={chartData.width - chartData.paddingX}
                            y2={y}
                            stroke="#f1f5f9"
                            strokeWidth="1"
                          />
                          <text
                            x={chartData.paddingX - 8}
                            y={y + 3}
                            textAnchor="end"
                            fontSize="10"
                            fill="#94a3b8"
                            fontWeight="500"
                          >
                            {labelVal}
                          </text>
                        </g>
                      );
                    })}

                    {/* Area under bookings */}
                    <path d={chartData.areaPath} fill="url(#bookingsGradient)" />

                    {/* Lines */}
                    <path
                      d={chartData.bookingsPath}
                      fill="none"
                      stroke="#2563eb"
                      strokeWidth="2.5"
                      strokeLinecap="round"
                    />
                    <path
                      d={chartData.deliveredPath}
                      fill="none"
                      stroke="#10b981"
                      strokeWidth="2.5"
                      strokeLinecap="round"
                    />
                    <path
                      d={chartData.failedPath}
                      fill="none"
                      stroke="#f43f5e"
                      strokeWidth="2"
                      strokeLinecap="round"
                    />

                    {/* Interactive Points */}
                    {chartData.points.map((p, idx) => {
                      const isHovered = hoveredPoint?.date === p.metric.date;
                      return (
                        <g key={idx} className="cursor-pointer">
                          {/* Invisible hit target */}
                          <circle
                            cx={p.x}
                            cy={p.yBookings}
                            r="12"
                            fill="transparent"
                            onMouseEnter={() => setHoveredPoint(p.metric)}
                            onMouseLeave={() => setHoveredPoint(null)}
                          />

                          {/* Data dots */}
                          <circle
                            cx={p.x}
                            cy={p.yBookings}
                            r={isHovered ? 5 : 3.5}
                            fill="#2563eb"
                            stroke="#ffffff"
                            strokeWidth="2"
                          />
                          <circle
                            cx={p.x}
                            cy={p.yDelivered}
                            r={isHovered ? 5 : 3}
                            fill="#10b981"
                            stroke="#ffffff"
                            strokeWidth="1.5"
                          />
                          {p.metric.failedCount > 0 && (
                            <circle
                              cx={p.x}
                              cy={p.yFailed}
                              r={isHovered ? 4.5 : 2.5}
                              fill="#f43f5e"
                              stroke="#ffffff"
                              strokeWidth="1.5"
                            />
                          )}

                          {/* Date Label on X Axis */}
                          <text
                            x={p.x}
                            y={chartData.height - 8}
                            textAnchor="middle"
                            fontSize="10"
                            fill="#64748b"
                            fontWeight="500"
                          >
                            {p.metric.date.slice(5)}
                          </text>
                        </g>
                      );
                    })}
                  </svg>
                </div>

                {/* Hover Tooltip Card */}
                {hoveredPoint && (
                  <div className="absolute top-2 right-4 bg-slate-900 text-white rounded-xl p-3 shadow-xl border border-slate-800 text-xs space-y-1 pointer-events-none animate-in fade-in duration-150">
                    <p className="font-bold border-b border-slate-800 pb-1 text-slate-300">
                      Date: {hoveredPoint.date}
                    </p>
                    <div className="flex justify-between gap-4">
                      <span className="text-blue-300">Bookings:</span>
                      <span className="font-mono font-bold">{hoveredPoint.totalBookings}</span>
                    </div>
                    <div className="flex justify-between gap-4">
                      <span className="text-emerald-300">Delivered:</span>
                      <span className="font-mono font-bold">{hoveredPoint.deliveredCount}</span>
                    </div>
                    <div className="flex justify-between gap-4">
                      <span className="text-rose-300">Failed:</span>
                      <span className="font-mono font-bold">{hoveredPoint.failedCount}</span>
                    </div>
                    <div className="flex justify-between gap-4 pt-1 border-t border-slate-800 text-[11px]">
                      <span className="text-slate-400">Success Rate:</span>
                      <span className="font-mono font-bold text-emerald-400">
                        {hoveredPoint.successRatePercentage.toFixed(1)}%
                      </span>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>

        {/* Lower Grid: Failure Breakdown + Zone Performance */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Section 1: Failure Reason Breakdown */}
          <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-2xs flex flex-col justify-between">
            <div>
              <div className="pb-4 border-b border-slate-100">
                <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4 text-rose-600" />
                  Delivery Failure Root-Cause Breakdown
                </h3>
                <p className="text-xs text-slate-500 mt-0.5">
                  Categorical distribution of delivery exceptions requiring re-dispatch
                </p>
              </div>

              {/* Stacked Proportional Meter */}
              <div className="pt-5 space-y-4">
                {isLoading ? (
                  <div className="py-12 flex justify-center text-slate-400">
                    <Spinner size="md" />
                  </div>
                ) : !summary?.failureBreakdown || summary.failureBreakdown.length === 0 ? (
                  <div className="rounded-xl border border-emerald-200 bg-emerald-50/50 p-6 text-center text-xs text-emerald-800">
                    <CheckCircle className="w-8 h-8 text-emerald-600 mx-auto mb-2" />
                    <p className="font-bold text-sm">100% Fulfillment Quality</p>
                    <p className="mt-1 text-emerald-700">No delivery failures occurred within this date window.</p>
                  </div>
                ) : (
                  <>
                    {/* Multi-segment horizontal bar */}
                    <div className="h-4 rounded-full bg-slate-100 overflow-hidden flex shadow-inner">
                      {summary.failureBreakdown.map((item, idx) => {
                        const colorClass = failureColors[idx % failureColors.length].split(' ')[0];
                        return (
                          <div
                            key={item.failureReason}
                            style={{ width: `${Math.max(item.percentage, 2)}%` }}
                            className={`${colorClass} transition-all`}
                            title={`${formatReasonLabel(item.failureReason)}: ${item.count} (${item.percentage.toFixed(1)}%)`}
                          />
                        );
                      })}
                    </div>

                    {/* Table of Failure Reasons */}
                    <div className="overflow-x-auto pt-2">
                      <table className="min-w-full divide-y divide-slate-100 text-left text-xs">
                        <thead>
                          <tr className="text-slate-400 uppercase tracking-wider text-[10px]">
                            <th className="pb-2 font-semibold">Failure Cause</th>
                            <th className="pb-2 font-semibold text-right">Incidents</th>
                            <th className="pb-2 font-semibold text-right">Share (%)</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-50">
                          {summary.failureBreakdown.map((item, idx) => {
                            const colorDot = failureColors[idx % failureColors.length].split(' ')[0];
                            return (
                              <tr key={item.failureReason} className="hover:bg-slate-50/70">
                                <td className="py-2.5 font-medium text-slate-700 flex items-center gap-2">
                                  <span className={`w-2.5 h-2.5 rounded-full ${colorDot} shrink-0`} />
                                  <span>{formatReasonLabel(item.failureReason)}</span>
                                </td>
                                <td className="py-2.5 font-mono font-bold text-slate-800 text-right">
                                  {item.count}
                                </td>
                                <td className="py-2.5 font-mono text-slate-500 text-right">
                                  {item.percentage.toFixed(1)}%
                                </td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    </div>
                  </>
                )}
              </div>
            </div>

            <p className="mt-4 pt-3 border-t border-slate-100 text-[11px] text-slate-400">
              Exceptions trigger automated reverse logistics workflows and customer alerts.
            </p>
          </div>

          {/* Section 2: Geographic Zone Performance */}
          <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-2xs flex flex-col justify-between">
            <div>
              <div className="pb-4 border-b border-slate-100">
                <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
                  <MapPin className="w-4 h-4 text-blue-600" />
                  Island-Wide Logistics Zone Performance
                </h3>
                <p className="text-xs text-slate-500 mt-0.5">
                  Fulfillment success rate and throughput aggregated by operational hub
                </p>
              </div>

              <div className="pt-4">
                {isLoading ? (
                  <div className="py-16 flex justify-center text-slate-400">
                    <Spinner size="md" />
                  </div>
                ) : !summary?.zonePerformance || summary.zonePerformance.length === 0 ? (
                  <div className="py-12 text-center text-slate-400 text-xs">
                    <Layers className="w-8 h-8 mx-auto mb-2 opacity-50" />
                    <p className="font-semibold text-slate-600">No zone performance data available</p>
                    <p className="mt-1">Bookings will automatically aggregate by origin hub.</p>
                  </div>
                ) : (
                  <div className="overflow-x-auto max-h-72 overflow-y-auto pr-1">
                    <table className="min-w-full divide-y divide-slate-100 text-left text-xs">
                      <thead className="bg-slate-50/70 sticky top-0">
                        <tr className="text-slate-400 uppercase tracking-wider text-[10px]">
                          <th className="py-2 px-2 font-semibold">Zone Hub</th>
                          <th className="py-2 px-2 font-semibold text-right">Handled</th>
                          <th className="py-2 px-2 font-semibold text-right">Delivered</th>
                          <th className="py-2 px-2 font-semibold text-right">Success Rate</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-50">
                        {summary.zonePerformance.map((zone) => {
                          const rate = zone.successRatePercentage ?? 0;
                          let progressColor = 'bg-emerald-500';
                          if (rate < 75) progressColor = 'bg-amber-500';
                          if (rate < 60) progressColor = 'bg-rose-500';

                          return (
                            <tr key={zone.zoneCode} className="hover:bg-slate-50/70">
                              <td className="py-2.5 px-2">
                                <span className="font-bold text-slate-900">
                                  {zone.zoneCode} — {zone.zoneName}
                                </span>
                                <span className="block text-[10px] text-slate-400 font-normal">
                                  {zone.district} District
                                </span>
                              </td>
                              <td className="py-2.5 px-2 font-mono font-semibold text-slate-700 text-right">
                                {zone.totalParcels}
                              </td>
                              <td className="py-2.5 px-2 font-mono font-semibold text-emerald-700 text-right">
                                {zone.deliveredParcels}
                              </td>
                              <td className="py-2.5 px-2 text-right">
                                <div className="inline-flex flex-col items-end gap-1">
                                  <span className="font-mono font-bold text-slate-900">{rate.toFixed(1)}%</span>
                                  <div className="w-16 h-1.5 rounded-full bg-slate-100 overflow-hidden">
                                    <div
                                      style={{ width: `${Math.min(100, Math.max(0, rate))}%` }}
                                      className={`h-full ${progressColor}`}
                                    />
                                  </div>
                                </div>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>

            <p className="mt-4 pt-3 border-t border-slate-100 text-[11px] text-slate-400">
              Zones operating below 75% fulfillment receive automated fleet rebalancing.
            </p>
          </div>
        </div>
      </div>
    </AppShell>
  );
}
