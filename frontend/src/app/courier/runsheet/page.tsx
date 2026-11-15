'use client';

import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import AppShell from '@/components/layout/AppShell';
import CourierShiftToggle from '@/components/courier/CourierShiftToggle';
import { getCourierRunsheet, updateParcelStatus } from '@/services/courierService';
import { CourierParcelCard, CourierRunsheetResponse, ParcelStatus } from '@/types/courier';
import { useToast } from '@/components/layout/ToastContext';
import {
  Package,
  Truck,
  MapPin,
  Phone,
  ArrowRight,
  RefreshCw,
  Search,
  CheckCircle2,
  AlertTriangle,
  Clock,
  ChevronRight,
  ShieldAlert,
  Inbox,
  Navigation,
} from 'lucide-react';

type RunsheetTab = 'ASSIGNED' | 'PICKED_UP' | 'IN_TRANSIT' | 'OUT_FOR_DELIVERY' | 'FAILED';

export default function CourierRunsheetPage() {
  const toast = useToast();

  const [runsheet, setRunsheet] = useState<CourierRunsheetResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [activeTab, setActiveTab] = useState<RunsheetTab>('ASSIGNED');
  const [searchQuery, setSearchQuery] = useState('');
  const [actionInProgress, setActionInProgress] = useState<string | null>(null);

  const fetchRunsheet = useCallback(async (isManualRefresh = false) => {
    if (isManualRefresh) setRefreshing(true);
    try {
      const data = await getCourierRunsheet();
      setRunsheet(data);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to load daily runsheet.';
      toast.error(message);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [toast]);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      void fetchRunsheet();
    }, 0);

    return () => window.clearTimeout(timeoutId);
  }, [fetchRunsheet]);

  // Quick action: Confirm pickup directly from runsheet
  const handleConfirmPickup = async (parcelId: string, trackingNumber: string) => {
    setActionInProgress(parcelId);
    try {
      await updateParcelStatus(parcelId, {
        targetStatus: 'PICKED_UP',
        deliveryNote: 'Picked up from sender via quick runsheet action',
      });
      toast.success(`Parcel ${trackingNumber} marked as PICKED UP!`);
      await fetchRunsheet();
      setActiveTab('PICKED_UP');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to update pickup status.';
      toast.error(msg);
    } finally {
      setActionInProgress(null);
    }
  };

  const getStatusBadge = (status: ParcelStatus) => {
    switch (status) {
      case 'ASSIGNED':
      case 'CREATED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-indigo-50 text-indigo-700 border border-indigo-200">
            <Clock className="w-3 h-3" /> Ready for Pickup
          </span>
        );
      case 'PICKED_UP':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-sky-50 text-sky-700 border border-sky-200">
            <Package className="w-3 h-3" /> Picked Up
          </span>
        );
      case 'IN_TRANSIT':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-50 text-amber-700 border border-amber-200">
            <Truck className="w-3 h-3" /> In Transit
          </span>
        );
      case 'OUT_FOR_DELIVERY':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
            <Navigation className="w-3 h-3 text-emerald-600 animate-pulse" /> Out for Delivery
          </span>
        );
      case 'FAILED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-rose-50 text-rose-700 border border-rose-200">
            <AlertTriangle className="w-3 h-3" /> Attempt Failed
          </span>
        );
      case 'DELIVERED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800 border border-emerald-300">
            <CheckCircle2 className="w-3 h-3" /> Delivered
          </span>
        );
      case 'RETURNED_TO_SENDER':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-slate-100 text-slate-700 border border-slate-300">
            <ShieldAlert className="w-3 h-3" /> Returned to Sender
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-slate-100 text-slate-600">
            {status}
          </span>
        );
    }
  };

  const filterParcels = (parcels: CourierParcelCard[]) => {
    if (!searchQuery.trim()) return parcels;
    const query = searchQuery.toLowerCase();
    return parcels.filter(
      (p) =>
        p.trackingNumber.toLowerCase().includes(query) ||
        p.recipientName.toLowerCase().includes(query) ||
        p.dropoffAddress.toLowerCase().includes(query) ||
        p.pickupAddress.toLowerCase().includes(query)
    );
  };

  const allParcels = runsheet ? [...runsheet.pickups, ...runsheet.deliveries] : [];
  const assignedAll = allParcels.filter((p) => p.currentStatus === 'ASSIGNED' || p.currentStatus === 'CREATED');
  const pickedUpAll = allParcels.filter((p) => p.currentStatus === 'PICKED_UP');
  const inTransitAll = allParcels.filter((p) => p.currentStatus === 'IN_TRANSIT');
  const outForDeliveryAll = allParcels.filter((p) => p.currentStatus === 'OUT_FOR_DELIVERY');
  const failedAll = allParcels.filter((p) => p.currentStatus === 'FAILED');

  const stageConfig: Record<
    RunsheetTab,
    {
      label: string;
      shortLabel: string;
      items: CourierParcelCard[];
      emptyTitle: string;
      emptyDesc: string;
      activeClass: string;
      metricActiveClass: string;
      metricLabelClass: string;
      metricValueClass: string;
      icon: React.ReactNode;
    }
  > = {
    ASSIGNED: {
      label: 'Assigned',
      shortLabel: 'Assigned',
      items: filterParcels(assignedAll),
      emptyTitle: 'No assigned pickups',
      emptyDesc: 'All assigned pickup requests have been collected or none assigned yet.',
      activeClass: 'bg-white text-indigo-700 shadow-xs',
      metricActiveClass: 'bg-indigo-50/80 border-indigo-200 shadow-xs',
      metricLabelClass: 'text-indigo-700',
      metricValueClass: 'text-indigo-900',
      icon: <Clock className="w-4 h-4" />,
    },
    PICKED_UP: {
      label: 'Picked Up',
      shortLabel: 'Picked Up',
      items: filterParcels(pickedUpAll),
      emptyTitle: 'No picked up parcels',
      emptyDesc: 'Confirm pickups from the Assigned tab to move them here.',
      activeClass: 'bg-white text-sky-700 shadow-xs',
      metricActiveClass: 'bg-sky-50/80 border-sky-200 shadow-xs',
      metricLabelClass: 'text-sky-700',
      metricValueClass: 'text-sky-900',
      icon: <Package className="w-4 h-4" />,
    },
    IN_TRANSIT: {
      label: 'In Transit',
      shortLabel: 'In Transit',
      items: filterParcels(inTransitAll),
      emptyTitle: 'No parcels in transit',
      emptyDesc: 'Parcels traveling between origin and destination hubs will appear here.',
      activeClass: 'bg-white text-amber-700 shadow-xs',
      metricActiveClass: 'bg-amber-50/80 border-amber-200 shadow-xs',
      metricLabelClass: 'text-amber-700',
      metricValueClass: 'text-amber-900',
      icon: <Truck className="w-4 h-4" />,
    },
    OUT_FOR_DELIVERY: {
      label: 'Out for Delivery',
      shortLabel: 'Out for Delivery',
      items: filterParcels(outForDeliveryAll),
      emptyTitle: 'No parcels out for delivery',
      emptyDesc: 'Parcels dispatched for final doorstep delivery will appear here.',
      activeClass: 'bg-white text-emerald-700 shadow-xs',
      metricActiveClass: 'bg-emerald-50/80 border-emerald-200 shadow-xs',
      metricLabelClass: 'text-emerald-700',
      metricValueClass: 'text-emerald-900',
      icon: <Navigation className="w-4 h-4" />,
    },
    FAILED: {
      label: 'Failed',
      shortLabel: 'Failed',
      items: filterParcels(failedAll),
      emptyTitle: 'No failed deliveries',
      emptyDesc: 'Any failed delivery attempts needing re-attempt or return will appear here.',
      activeClass: 'bg-white text-rose-700 shadow-xs',
      metricActiveClass: 'bg-rose-50/80 border-rose-200 shadow-xs',
      metricLabelClass: 'text-rose-700',
      metricValueClass: 'text-rose-900',
      icon: <AlertTriangle className="w-4 h-4" />,
    },
  };

  const tabOrder: RunsheetTab[] = ['ASSIGNED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'FAILED'];
  const currentStage = stageConfig[activeTab];

  return (
    <AppShell>
      <div className="max-w-3xl mx-auto space-y-4 sm:space-y-6 pb-12">
        {/* Top Header Card */}
        <div className="bg-white rounded-2xl p-4 sm:p-6 shadow-sm border border-slate-200/80">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div>
              <div className="flex items-center gap-2">
                <div className="w-9 h-9 rounded-xl bg-emerald-600 flex items-center justify-center text-white shadow-sm shadow-emerald-200">
                  <Truck className="w-5 h-5" />
                </div>
                <div>
                  <h1 className="text-xl sm:text-2xl font-bold text-slate-900 tracking-tight">
                    Courier Runsheet
                  </h1>
                  <p className="text-xs text-slate-500 font-medium">
                    Daily Active Pickup & Delivery Tasks
                  </p>
                </div>
              </div>
            </div>

            {/* Shift Availability Toggle */}
            <div className="w-full sm:w-auto">
              <CourierShiftToggle />
            </div>
          </div>

          {/* Quick Metrics Bar */}
          <div className="grid grid-cols-2 sm:grid-cols-5 gap-2 sm:gap-2.5 mt-4 pt-4 border-t border-slate-100">
            <div className="col-span-2 sm:col-span-1 bg-slate-50 rounded-xl p-2.5 sm:p-3 text-center border border-slate-200/60">
              <span className="text-[11px] font-semibold uppercase tracking-wider text-slate-500">
                Active Total
              </span>
              <p className="text-xl sm:text-2xl font-extrabold text-slate-900">
                {runsheet ? runsheet.totalActiveParcels : 0}
              </p>
            </div>

            {(['ASSIGNED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY'] as RunsheetTab[]).map((tabKey) => {
              const cfg = stageConfig[tabKey];
              const rawCount =
                tabKey === 'ASSIGNED'
                  ? assignedAll.length
                  : tabKey === 'PICKED_UP'
                  ? pickedUpAll.length
                  : tabKey === 'IN_TRANSIT'
                  ? inTransitAll.length
                  : outForDeliveryAll.length;
              return (
                <div
                  key={tabKey}
                  onClick={() => setActiveTab(tabKey)}
                  className={`rounded-xl p-2.5 sm:p-3 text-center border cursor-pointer transition-all ${
                    activeTab === tabKey
                      ? cfg.metricActiveClass
                      : 'bg-slate-50 border-slate-200/60 hover:bg-slate-100/60'
                  }`}
                >
                  <span className={`text-[11px] font-semibold uppercase tracking-wider ${cfg.metricLabelClass}`}>
                    {cfg.shortLabel}
                  </span>
                  <p className={`text-xl sm:text-2xl font-extrabold ${cfg.metricValueClass}`}>
                    {rawCount}
                  </p>
                </div>
              );
            })}
          </div>
        </div>

        {/* Search & Refresh Actions */}
        <div className="flex items-center gap-2">
          <div className="relative flex-1">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search by tracking #, name, or address..."
              className="w-full pl-9 pr-4 py-2.5 text-sm bg-white border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent shadow-2xs"
            />
          </div>

          <button
            onClick={() => fetchRunsheet(true)}
            disabled={refreshing}
            className="p-2.5 bg-white border border-slate-200 text-slate-600 rounded-xl hover:bg-slate-50 hover:text-slate-900 transition-colors shadow-2xs shrink-0"
            title="Refresh Runsheet"
          >
            <RefreshCw className={`w-4 h-4 ${refreshing ? 'animate-spin text-emerald-600' : ''}`} />
          </button>
        </div>

        {/* Stage Tab Navigation */}
        <div className="flex items-center gap-1 overflow-x-auto border-b border-slate-200 bg-white/70 p-1 rounded-xl backdrop-blur-xs">
          {tabOrder.map((tabKey) => {
            const cfg = stageConfig[tabKey];
            const count =
              tabKey === 'ASSIGNED'
                ? assignedAll.length
                : tabKey === 'PICKED_UP'
                ? pickedUpAll.length
                : tabKey === 'IN_TRANSIT'
                ? inTransitAll.length
                : tabKey === 'OUT_FOR_DELIVERY'
                ? outForDeliveryAll.length
                : failedAll.length;
            return (
              <button
                key={tabKey}
                onClick={() => setActiveTab(tabKey)}
                className={`flex-1 min-w-fit px-3 py-2.5 text-center font-semibold text-xs sm:text-sm rounded-lg transition-all flex items-center justify-center gap-1.5 whitespace-nowrap ${
                  activeTab === tabKey
                    ? cfg.activeClass
                    : 'text-slate-500 hover:text-slate-900'
                }`}
              >
                {cfg.icon}
                <span>{cfg.label}</span>
                <span className="px-1.5 py-0.2 rounded-full text-[11px] bg-slate-100 text-slate-700">
                  {count}
                </span>
              </button>
            );
          })}
        </div>

        {/* Content Section */}
        {loading ? (
          <div className="p-12 text-center bg-white rounded-2xl border border-slate-200 shadow-2xs">
            <RefreshCw className="w-8 h-8 mx-auto text-emerald-600 animate-spin mb-3" />
            <p className="text-sm font-medium text-slate-600">Loading daily runsheet...</p>
          </div>
        ) : activeTab === 'ASSIGNED' ? (
          /* Assigned (Pickups) List */
          <div className="space-y-3">
            {currentStage.items.length === 0 ? (
              <div className="p-12 text-center bg-white rounded-2xl border border-slate-200 shadow-2xs">
                <Inbox className="w-10 h-10 mx-auto text-slate-300 mb-2" />
                <h3 className="font-semibold text-slate-700">{currentStage.emptyTitle}</h3>
                <p className="text-xs text-slate-500 mt-1">{currentStage.emptyDesc}</p>
              </div>
            ) : (
              currentStage.items.map((parcel) => (
                <div
                  key={parcel.id}
                  className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200 shadow-2xs hover:shadow-xs transition-shadow space-y-3"
                >
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <span className="font-mono font-bold text-sm text-slate-900 tracking-wider">
                        {parcel.trackingNumber}
                      </span>
                      <p className="text-xs text-slate-500 flex items-center gap-1 mt-0.5">
                        <span>{parcel.originZoneName || 'Zone A'}</span>
                        <ArrowRight className="w-3 h-3 text-slate-400" />
                        <span>{parcel.destinationZoneName || 'Zone B'}</span>
                      </p>
                      {parcel.destinationHubLocation && (
                        <p className="text-[11px] text-indigo-600/80 mt-0.5 truncate max-w-md">
                          Hub: {parcel.destinationHubLocation}
                        </p>
                      )}
                    </div>
                    {getStatusBadge(parcel.currentStatus)}
                  </div>

                  {/* Pickup Address */}
                  <div className="bg-slate-50 p-3 rounded-xl border border-slate-100 text-xs space-y-1.5">
                    <div className="flex items-start gap-2">
                      <MapPin className="w-4 h-4 text-indigo-600 shrink-0 mt-0.5" />
                      <div>
                        <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                          Pickup Address
                        </span>
                        <p className="text-slate-800 font-medium">{parcel.pickupAddress}</p>
                      </div>
                    </div>
                  </div>

                  {/* Parcel Specs & Action Buttons */}
                  <div className="flex items-center justify-between pt-1">
                    <div className="text-xs text-slate-600">
                      <span className="font-medium text-slate-800">{parcel.weightKg} kg</span> •{' '}
                      <span className="text-slate-500">{parcel.parcelSize}</span> •{' '}
                      <span className="font-semibold text-emerald-700">
                        LKR {Number(parcel.estimatedFee).toFixed(2)}
                      </span>
                    </div>

                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => handleConfirmPickup(parcel.id, parcel.trackingNumber)}
                        disabled={actionInProgress === parcel.id}
                        className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-semibold flex items-center gap-1.5 transition-colors shadow-2xs disabled:opacity-50"
                      >
                        {actionInProgress === parcel.id ? (
                          <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                        ) : (
                          <CheckCircle2 className="w-3.5 h-3.5" />
                        )}
                        Confirm Pickup
                      </button>

                      <Link
                        href={`/courier/deliver/${parcel.id}`}
                        className="p-1.5 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-xl transition-colors"
                        title="View Details"
                      >
                        <ChevronRight className="w-4 h-4" />
                      </Link>
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>
        ) : (
          /* Stage Delivery / Transit List */
          <div className="space-y-3">
            {currentStage.items.length === 0 ? (
              <div className="p-12 text-center bg-white rounded-2xl border border-slate-200 shadow-2xs">
                <Inbox className="w-10 h-10 mx-auto text-slate-300 mb-2" />
                <h3 className="font-semibold text-slate-700">{currentStage.emptyTitle}</h3>
                <p className="text-xs text-slate-500 mt-1">{currentStage.emptyDesc}</p>
              </div>
            ) : (
              currentStage.items.map((parcel) => (
                <div
                  key={parcel.id}
                  className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200 shadow-2xs hover:shadow-xs transition-shadow space-y-3"
                >
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <span className="font-mono font-bold text-sm text-slate-900 tracking-wider">
                        {parcel.trackingNumber}
                      </span>
                      <p className="text-xs text-slate-500 flex items-center gap-1 mt-0.5">
                        <span>{parcel.originZoneName || 'Zone A'}</span>
                        <ArrowRight className="w-3 h-3 text-slate-400" />
                        <span>{parcel.destinationZoneName || 'Zone B'}</span>
                      </p>
                      {parcel.destinationHubLocation && (
                        <p className="text-[11px] text-indigo-600/80 mt-0.5 truncate max-w-md">
                          Hub: {parcel.destinationHubLocation}
                        </p>
                      )}
                    </div>

                    <div className="flex items-center gap-1.5">
                      {parcel.failureCount > 0 && (
                        <span className="inline-flex items-center gap-0.5 px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-100 text-amber-800 border border-amber-300">
                          <AlertTriangle className="w-2.5 h-2.5" />
                          Attempt {parcel.failureCount}/3
                        </span>
                      )}
                      {getStatusBadge(parcel.currentStatus)}
                    </div>
                  </div>

                  {/* Recipient & Destination Details */}
                  <div className="bg-slate-50 p-3 rounded-xl border border-slate-100 text-xs space-y-2">
                    <div className="flex items-start gap-2 pb-2 border-b border-slate-200/60">
                      <MapPin className="w-4 h-4 text-indigo-500 shrink-0 mt-0.5" />
                      <div>
                        <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                          Pickup Address
                        </span>
                        <p className="text-slate-700 font-medium">{parcel.pickupAddress}</p>
                      </div>
                    </div>

                    <div className="flex items-start justify-between gap-2">
                      <div className="flex items-start gap-2">
                        <MapPin className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
                        <div>
                          <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                            Dropoff Address
                          </span>
                          <p className="text-slate-800 font-medium">{parcel.dropoffAddress}</p>
                        </div>
                      </div>

                      {/* Click-to-call Phone Button */}
                      {parcel.recipientPhone && (
                        <a
                          href={`tel:${parcel.recipientPhone}`}
                          className="flex items-center gap-1 px-2.5 py-1 bg-emerald-50 text-emerald-700 hover:bg-emerald-100 rounded-lg text-xs font-semibold border border-emerald-200 transition-colors shrink-0"
                          title="Call Recipient"
                        >
                          <Phone className="w-3 h-3" />
                          Call
                        </a>
                      )}
                    </div>

                    <div className="text-[11px] text-slate-500 pl-6">
                      Recipient: <span className="font-semibold text-slate-700">{parcel.recipientName}</span>
                    </div>
                  </div>

                  {/* Action Bar */}
                  <div className="flex items-center justify-between pt-1">
                    <div className="text-xs text-slate-600">
                      <span className="font-medium text-slate-800">{parcel.weightKg} kg</span> •{' '}
                      <span className="font-semibold text-emerald-700">
                        LKR {Number(parcel.estimatedFee).toFixed(2)}
                      </span>
                    </div>

                    <Link
                      href={`/courier/deliver/${parcel.id}`}
                      className="px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-semibold flex items-center gap-1.5 transition-colors shadow-2xs"
                    >
                      <Navigation className="w-3.5 h-3.5" />
                      Execute Delivery
                      <ChevronRight className="w-3.5 h-3.5 ml-0.5" />
                    </Link>
                  </div>
                </div>
              ))
            )}
          </div>
        )}
      </div>
    </AppShell>
  );
}
