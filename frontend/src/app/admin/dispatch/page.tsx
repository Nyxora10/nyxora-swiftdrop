'use client';

import React, { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import axios from 'axios';
import AppShell from '@/components/layout/AppShell';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import Spinner from '@/components/ui/Spinner';
import { useToast } from '@/components/layout/ToastContext';
import {
  Truck,
  Package,
  AlertCircle,
  CheckCircle2,
  RefreshCw,
  X,
  ArrowRight,
  UserCheck,
  ShieldAlert,
  MapPin,
  Scale,
  Clock,
  Phone,
  AlertTriangle,
  Inbox,
  Check,
} from 'lucide-react';
import {
  getUnassignedParcels,
  getAvailableCouriers,
  assignCourier,
} from '@/services/dispatchService';
import { getDeliveryZones } from '@/services/zoneService';
import {
  UnassignedParcelResponse,
  AvailableCourierResponse,
  ParcelSize,
} from '@/types/dispatch';
import { DeliveryZone } from '@/types/booking';

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

/**
 * Merges freshly fetched courier list with any un-reconciled optimistic updates
 * to avoid rendering stale reads during RabbitMQ cross-service propagation.
 */
function applyOptimisticWorkloads(
  fetchedCouriers: AvailableCourierResponse[],
  pendingReconciliations: Map<string, number>
): AvailableCourierResponse[] {
  return fetchedCouriers.map((courier) => {
    const expectedMin = pendingReconciliations.get(courier.courierId);
    if (expectedMin === undefined) {
      return courier;
    }

    // If backend has reached or exceeded our optimistic expectation, reconciliation is complete
    if (courier.activeDeliveryCount >= expectedMin) {
      pendingReconciliations.delete(courier.courierId);
      return courier;
    }

    // Backend is still returning pre-event count (stale response); preserve optimistic state
    const maxW = courier.maxWorkload || 20;
    const optimisticActive = expectedMin;
    return {
      ...courier,
      activeDeliveryCount: optimisticActive,
      capacityRemaining: Math.max(0, maxW - optimisticActive),
      canAssign: courier.isAvailable && optimisticActive < maxW,
    };
  });
}

export default function AdminDispatchBoardPage() {
  const toast = useToast();

  // State
  const [parcels, setParcels] = useState<UnassignedParcelResponse[]>([]);
  const [couriers, setCouriers] = useState<AvailableCourierResponse[]>([]);
  const [zones, setZones] = useState<DeliveryZone[]>([]);
  const [selectedZoneId, setSelectedZoneId] = useState<number | null>(null);

  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);
  const [pageNotice, setPageNotice] = useState<string | null>(null);

  // Modal State
  const [selectedParcel, setSelectedParcel] = useState<UnassignedParcelResponse | null>(null);
  const [selectedCourierId, setSelectedCourierId] = useState<string>('');
  const [modalCouriers, setModalCouriers] = useState<AvailableCourierResponse[]>([]);
  const [isLoadingModalCouriers, setIsLoadingModalCouriers] = useState(false);
  const [isAssigning, setIsAssigning] = useState(false);
  const [assignmentError, setAssignmentError] = useState<string | null>(null);

  // Stale request guard & asynchronous reconciliation tracking
  const loadSeqRef = useRef(0);
  const pendingReconciliationsRef = useRef<Map<string, number>>(new Map());
  const reconcileTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  // Clean up any pending reconciliation timer on unmount
  useEffect(() => {
    return () => {
      if (reconcileTimeoutRef.current) {
        clearTimeout(reconcileTimeoutRef.current);
      }
    };
  }, []);

  // Load logistics zones once on mount
  useEffect(() => {
    let isMounted = true;
    async function loadZones() {
      try {
        const zoneList = await getDeliveryZones();
        if (isMounted) {
          setZones(zoneList);
        }
      } catch (err) {
        console.warn('[DispatchBoard] Failed to load delivery zones:', err);
      }
    }
    void loadZones();
    return () => {
      isMounted = false;
    };
  }, []);

  // Fetch unassigned parcels and available couriers
  const fetchDispatchData = useCallback(
    async (isRefresh = false, clearPendingReconciliation = false) => {
      const seq = ++loadSeqRef.current;
      if (isRefresh) {
        setIsRefreshing(true);
      } else {
        setIsLoading(true);
      }
      setPageError(null);

      if (clearPendingReconciliation) {
        if (reconcileTimeoutRef.current) {
          clearTimeout(reconcileTimeoutRef.current);
          reconcileTimeoutRef.current = null;
        }
        pendingReconciliationsRef.current.clear();
      }

      try {
        const [parcelData, courierData] = await Promise.all([
          getUnassignedParcels(selectedZoneId),
          getAvailableCouriers(selectedZoneId),
        ]);

        if (seq !== loadSeqRef.current) return; // Discard stale response

        const rawCouriers = Array.isArray(courierData) ? courierData : [];
        const mergedCouriers = applyOptimisticWorkloads(
          rawCouriers,
          pendingReconciliationsRef.current
        );

        setParcels(Array.isArray(parcelData) ? parcelData : []);
        setCouriers(mergedCouriers);
      } catch (err) {
        if (seq !== loadSeqRef.current) return;
        setPageError(readApiError(err, 'Failed to retrieve dispatch queue. Please try again.'));
        setParcels([]);
        setCouriers([]);
      } finally {
        if (seq === loadSeqRef.current) {
          setIsLoading(false);
          setIsRefreshing(false);
        }
      }
    },
    [selectedZoneId]
  );

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- this effect synchronizes remote dispatch data into the page state.
    void fetchDispatchData();
  }, [fetchDispatchData]);

  // Short, bounded background re-fetch to reconcile with auth-service after RabbitMQ processing
  const startBackgroundReconciliation = useCallback(
    (zoneId: number | null) => {
      if (reconcileTimeoutRef.current) {
        clearTimeout(reconcileTimeoutRef.current);
        reconcileTimeoutRef.current = null;
      }

      let attempt = 0;
      const maxAttempts = 3;
      const delays = [600, 1000, 1500];

      const reconcileStep = async () => {
        attempt++;
        try {
          const freshCouriers = await getAvailableCouriers(zoneId);
          if (Array.isArray(freshCouriers)) {
            let allReconciled = true;
            for (const c of freshCouriers) {
              const expectedMin = pendingReconciliationsRef.current.get(c.courierId);
              if (expectedMin !== undefined) {
                if (c.activeDeliveryCount >= expectedMin) {
                  pendingReconciliationsRef.current.delete(c.courierId);
                } else {
                  allReconciled = false;
                }
              }
            }

            setCouriers(applyOptimisticWorkloads(freshCouriers, pendingReconciliationsRef.current));

            // Stop retrying once the backend confirms all pending counts
            if (allReconciled || pendingReconciliationsRef.current.size === 0) {
              return;
            }
          }
        } catch (err) {
          console.warn('[DispatchBoard] Background reconciliation attempt failed:', err);
        }

        if (attempt < maxAttempts && pendingReconciliationsRef.current.size > 0) {
          const delay = delays[attempt] ?? 1000;
          reconcileTimeoutRef.current = setTimeout(reconcileStep, delay);
        } else if (pendingReconciliationsRef.current.size > 0) {
          // Bounded retries exhausted; clear pending map so subsequent manual refreshes reflect server
          pendingReconciliationsRef.current.clear();
        }
      };

      reconcileTimeoutRef.current = setTimeout(reconcileStep, delays[0]);
    },
    []
  );

  // Derived KPI metrics
  const totalUnassignedCount = parcels.length;
  const availableCourierCount = useMemo(
    () => couriers.filter((c) => c.isAvailable && c.canAssign).length,
    [couriers]
  );
  const totalAvailableCapacity = useMemo(
    () =>
      couriers
        .filter((c) => c.isAvailable)
        .reduce((sum, c) => sum + Math.max(0, (c.maxWorkload || 20) - c.activeDeliveryCount), 0),
    [couriers]
  );

  // Open courier assignment modal
  const handleOpenAssignModal = useCallback(async (parcel: UnassignedParcelResponse) => {
    setSelectedParcel(parcel);
    setSelectedCourierId('');
    setAssignmentError(null);
    setIsLoadingModalCouriers(true);

    try {
      // Prioritize couriers in the parcel's origin zone
      const zoneToQuery = parcel.originZoneId ?? selectedZoneId ?? null;
      const courierList = await getAvailableCouriers(zoneToQuery);
      setModalCouriers(Array.isArray(courierList) ? courierList : []);
    } catch (err) {
      setAssignmentError(readApiError(err, 'Unable to retrieve available couriers for this parcel.'));
      setModalCouriers([]);
    } finally {
      setIsLoadingModalCouriers(false);
    }
  }, [selectedZoneId]);

  const handleCloseAssignModal = useCallback(() => {
    if (isAssigning) return;
    setSelectedParcel(null);
    setSelectedCourierId('');
    setAssignmentError(null);
    setModalCouriers([]);
  }, [isAssigning]);

  // Execute parcel assignment
  const handleConfirmAssignment = async () => {
    if (!selectedParcel || !selectedCourierId || isAssigning) return;

    const chosenCourier = modalCouriers.find((c) => c.courierId === selectedCourierId);
    if (!chosenCourier) {
      setAssignmentError('Please select a valid courier.');
      return;
    }

    if (!chosenCourier.canAssign || chosenCourier.activeDeliveryCount >= (chosenCourier.maxWorkload || 20)) {
      setAssignmentError(
        `Courier ${chosenCourier.fullName} has reached the maximum workload limit of ${chosenCourier.maxWorkload || 20} parcels.`
      );
      return;
    }

    setIsAssigning(true);
    setAssignmentError(null);

    try {
      const response = await assignCourier(selectedParcel.id, chosenCourier.courierId);

      // Optimistic update: Immediately increment courier workload and update capacity
      const newActiveCount = chosenCourier.activeDeliveryCount + 1;
      const maxW = chosenCourier.maxWorkload || 20;
      const newCapacity = Math.max(0, maxW - newActiveCount);
      const newCanAssign = chosenCourier.isAvailable && newActiveCount < maxW;

      // Register expected minimum count to protect against stale reads during async RabbitMQ sync
      pendingReconciliationsRef.current.set(chosenCourier.courierId, newActiveCount);

      // Immediately update local couriers list
      setCouriers((prevCouriers) =>
        prevCouriers.map((c) =>
          c.courierId === chosenCourier.courierId
            ? {
                ...c,
                activeDeliveryCount: newActiveCount,
                capacityRemaining: newCapacity,
                canAssign: newCanAssign,
              }
            : c
        )
      );

      // Immediately remove assigned parcel from local queue
      setParcels((prevParcels) => prevParcels.filter((p) => p.id !== selectedParcel.id));

      toast.success(
        response.message || `Parcel ${selectedParcel.trackingNumber} successfully assigned to ${chosenCourier.fullName}.`
      );
      setPageNotice(
        `Parcel ${selectedParcel.trackingNumber} assigned to ${chosenCourier.fullName} (${chosenCourier.licensePlate}).`
      );
      handleCloseAssignModal();

      // Short, bounded background re-fetch to reconcile with auth-service after RabbitMQ processing
      startBackgroundReconciliation(selectedParcel.originZoneId ?? selectedZoneId ?? null);
    } catch (err) {
      const errMsg = readApiError(err, 'Courier assignment failed. Please try again.');
      setAssignmentError(errMsg);
      toast.error(errMsg);
    } finally {
      setIsAssigning(false);
    }
  };

  const getParcelSizeBadgeVariant = (size: ParcelSize): 'neutral' | 'info' | 'warning' => {
    switch (size) {
      case 'SMALL':
        return 'neutral';
      case 'MEDIUM':
        return 'info';
      case 'LARGE':
        return 'warning';
      default:
        return 'neutral';
    }
  };

  return (
    <AppShell>
      <div className="space-y-6">
        {/* Page Notification Banner */}
        {(pageError || pageNotice) && (
          <div
            className={`rounded-xl border px-4 py-3 text-sm flex items-center justify-between transition-all ${
              pageError
                ? 'border-red-200 bg-red-50 text-red-700'
                : 'border-emerald-200 bg-emerald-50 text-emerald-700'
            }`}
          >
            <div className="flex items-center gap-2">
              {pageError ? (
                <AlertCircle className="h-4 w-4 shrink-0" />
              ) : (
                <CheckCircle2 className="h-4 w-4 shrink-0" />
              )}
              <span>{pageError || pageNotice}</span>
            </div>
            <button
              onClick={() => {
                setPageError(null);
                setPageNotice(null);
              }}
              className="p-1 opacity-70 hover:opacity-100 transition-opacity cursor-pointer"
              aria-label="Dismiss banner"
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
                Operations Dispatch Board
              </h1>
              <p className="text-sm text-slate-500 mt-1">
                Review unassigned parcel queues, monitor courier capacity, and allocate delivery workloads.
              </p>
            </div>

            <div className="flex items-center gap-2.5">
              <Button
                variant="secondary"
                size="sm"
                onClick={() => void fetchDispatchData(true, true)}
                disabled={isRefreshing || isLoading}
                leftIcon={<RefreshCw className={`w-3.5 h-3.5 ${isRefreshing ? 'animate-spin' : ''}`} />}
              >
                {isRefreshing ? 'Refreshing...' : 'Refresh Queue'}
              </Button>
            </div>
          </div>
        </div>

        {/* Top KPI Summary Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          {/* Card 1: Unassigned Parcels */}
          <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-2xs">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Unassigned Queue</p>
                <div className="mt-2 flex items-baseline gap-2">
                  <span className="text-3xl font-extrabold tracking-tight text-slate-900">
                    {isLoading ? '...' : totalUnassignedCount}
                  </span>
                  <span className="text-xs text-slate-500">parcels pending</span>
                </div>
              </div>
              <div className="p-3 bg-amber-50 rounded-xl border border-amber-200/60 text-amber-600">
                <Package className="w-6 h-6" />
              </div>
            </div>
            <p className="mt-3 text-xs text-slate-400">Parcels in CREATED status awaiting dispatch allocation</p>
          </div>

          {/* Card 2: Available Couriers */}
          <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-2xs">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Active Couriers</p>
                <div className="mt-2 flex items-baseline gap-2">
                  <span className="text-3xl font-extrabold tracking-tight text-slate-900">
                    {isLoading ? '...' : availableCourierCount}
                  </span>
                  <span className="text-xs text-slate-500">on-duty &amp; ready</span>
                </div>
              </div>
              <div className="p-3 bg-blue-50 rounded-xl border border-blue-200/60 text-blue-600">
                <UserCheck className="w-6 h-6" />
              </div>
            </div>
            <p className="mt-3 text-xs text-slate-400">On-duty couriers with available workload capacity (&lt; 20)</p>
          </div>

          {/* Card 3: Total Workload Capacity */}
          <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-2xs">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Available Capacity</p>
                <div className="mt-2 flex items-baseline gap-2">
                  <span className="text-3xl font-extrabold tracking-tight text-slate-900">
                    {isLoading ? '...' : totalAvailableCapacity}
                  </span>
                  <span className="text-xs text-slate-500">delivery slots</span>
                </div>
              </div>
              <div className="p-3 bg-emerald-50 rounded-xl border border-emerald-200/60 text-emerald-600">
                <Truck className="w-6 h-6" />
              </div>
            </div>
            <p className="mt-3 text-xs text-slate-400">Total remaining allocation headroom across all active couriers</p>
          </div>
        </div>

        {/* Zone Filter Toolbar */}
        <div className="rounded-2xl border border-slate-200/80 bg-white p-4 shadow-2xs flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex items-center gap-2 text-slate-500 text-xs font-bold uppercase tracking-wider">
              <MapPin className="w-4 h-4 text-blue-600 shrink-0" />
              <span>Filter by Origin Hub:</span>
            </div>
            <select
              value={selectedZoneId ?? ''}
              onChange={(e) => setSelectedZoneId(e.target.value ? Number(e.target.value) : null)}
              className="py-2 px-3 rounded-xl border border-slate-200 bg-slate-50/50 text-xs font-medium text-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 focus:bg-white transition-all cursor-pointer"
            >
              <option value="">All Logistics Zones (Island-wide)</option>
              {zones.map((zone) => (
                <option key={zone.id} value={zone.id}>
                  {zone.zoneCode} — {zone.zoneName} ({zone.district})
                </option>
              ))}
            </select>
          </div>

          <div className="text-xs font-medium text-slate-500">
            Showing <span className="font-bold text-slate-900">{parcels.length}</span> unassigned parcel{parcels.length === 1 ? '' : 's'}
          </div>
        </div>

        {/* Unassigned Parcels Table */}
        <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xs">
          {isLoading ? (
            <div className="py-20 flex flex-col items-center justify-center text-slate-500">
              <Spinner size="lg" />
              <p className="mt-3 text-sm font-medium">Loading unassigned parcel queue...</p>
            </div>
          ) : parcels.length === 0 ? (
            <div className="py-16 px-4 text-center">
              <div className="mx-auto w-12 h-12 rounded-full bg-slate-100 flex items-center justify-center text-slate-400 mb-3">
                <Inbox className="w-6 h-6" />
              </div>
              <h3 className="text-base font-bold text-slate-900">Queue is Clear</h3>
              <p className="text-sm text-slate-500 mt-1 max-w-md mx-auto">
                {selectedZoneId
                  ? 'No unassigned parcels found for the selected origin hub. Try selecting All Zones or check back later.'
                  : 'All customer bookings have been dispatched to couriers. Great job!'}
              </p>
              {selectedZoneId && (
                <button
                  onClick={() => setSelectedZoneId(null)}
                  className="mt-4 inline-flex items-center gap-1.5 text-xs font-bold text-blue-600 hover:text-blue-700 cursor-pointer"
                >
                  Clear Hub Filter
                </button>
              )}
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="min-w-full divide-y divide-slate-200 text-left">
                <thead className="bg-slate-50/80">
                  <tr>
                    <th className="px-4 py-3.5 text-xs font-semibold uppercase tracking-wider text-slate-500">
                      Tracking Number
                    </th>
                    <th className="px-4 py-3.5 text-xs font-semibold uppercase tracking-wider text-slate-500">
                      Route (Origin → Destination)
                    </th>
                    <th className="px-4 py-3.5 text-xs font-semibold uppercase tracking-wider text-slate-500">
                      Recipient &amp; Destination
                    </th>
                    <th className="px-4 py-3.5 text-xs font-semibold uppercase tracking-wider text-slate-500">
                      Specs (Weight / Size)
                    </th>
                    <th className="px-4 py-3.5 text-xs font-semibold uppercase tracking-wider text-slate-500">
                      Estimated Fee
                    </th>
                    <th className="px-4 py-3.5 text-xs font-semibold uppercase tracking-wider text-slate-500">
                      Booked At
                    </th>
                    <th className="px-4 py-3.5 text-xs font-semibold uppercase tracking-wider text-slate-500 text-right">
                      Action
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 text-sm">
                  {parcels.map((parcel) => (
                    <tr key={parcel.id} className="hover:bg-slate-50/70 transition-colors">
                      {/* Tracking Number */}
                      <td className="px-4 py-4 whitespace-nowrap">
                        <div className="flex items-center gap-2">
                          <span className="font-mono font-bold text-xs text-blue-600 bg-blue-50/60 px-2.5 py-1 rounded-lg border border-blue-200/50">
                            {parcel.trackingNumber}
                          </span>
                        </div>
                      </td>

                      {/* Route */}
                      <td className="px-4 py-4 whitespace-nowrap">
                        <div className="flex items-center gap-1.5 text-xs font-medium text-slate-700">
                          <span
                            className="inline-flex items-center px-2 py-0.5 rounded-md bg-slate-100 text-slate-700 font-semibold"
                            title={parcel.originZoneName || 'Origin Hub'}
                          >
                            {parcel.originZoneCode || `Zone ${parcel.originZoneId || '?'}`}
                          </span>
                          <ArrowRight className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                          <span
                            className="inline-flex items-center px-2 py-0.5 rounded-md bg-blue-50 text-blue-700 font-semibold border border-blue-200/60"
                            title={parcel.destinationZoneName || 'Destination Hub'}
                          >
                            {parcel.destinationZoneCode || `Zone ${parcel.destinationZoneId || '?'}`}
                          </span>
                        </div>
                      </td>

                      {/* Recipient Details */}
                      <td className="px-4 py-4">
                        <div className="max-w-xs">
                          <p className="font-semibold text-xs text-slate-900 truncate">{parcel.recipientName}</p>
                          <div className="flex items-center gap-1 text-[11px] text-slate-500 mt-0.5">
                            <Phone className="w-3 h-3 text-slate-400 shrink-0" />
                            <span>{parcel.recipientPhone}</span>
                          </div>
                          <p className="text-[11px] text-slate-400 truncate mt-0.5" title={parcel.dropoffAddress}>
                            {parcel.dropoffAddress}
                          </p>
                        </div>
                      </td>

                      {/* Specs */}
                      <td className="px-4 py-4 whitespace-nowrap">
                        <div className="flex items-center gap-2">
                          <Badge variant={getParcelSizeBadgeVariant(parcel.parcelSize)} size="sm">
                            {parcel.parcelSize}
                          </Badge>
                          <span className="text-xs font-semibold text-slate-600 flex items-center gap-1">
                            <Scale className="w-3 h-3 text-slate-400" />
                            {parcel.weightKg ? Number(parcel.weightKg).toFixed(1) : '0.0'} kg
                          </span>
                        </div>
                      </td>

                      {/* Estimated Fee */}
                      <td className="px-4 py-4 whitespace-nowrap">
                        <span className="text-xs font-bold text-slate-900">
                          Rs. {Number(parcel.estimatedFee || 0).toFixed(2)}
                        </span>
                      </td>

                      {/* Booked Timestamp */}
                      <td className="px-4 py-4 whitespace-nowrap text-xs text-slate-500">
                        <div className="flex items-center gap-1">
                          <Clock className="w-3 h-3 text-slate-400" />
                          <span>
                            {parcel.createdAt
                              ? new Date(parcel.createdAt).toLocaleDateString(undefined, {
                                  month: 'short',
                                  day: 'numeric',
                                  hour: '2-digit',
                                  minute: '2-digit',
                                })
                              : '—'}
                          </span>
                        </div>
                      </td>

                      {/* Actions */}
                      <td className="px-4 py-4 whitespace-nowrap text-right">
                        <Button
                          variant="primary"
                          size="sm"
                          onClick={() => void handleOpenAssignModal(parcel)}
                          leftIcon={<UserCheck className="w-3.5 h-3.5" />}
                        >
                          Assign Courier
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Courier Assignment Confirmation Modal */}
        {selectedParcel && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs p-4 overflow-y-auto">
            <div className="w-full max-w-lg rounded-2xl border border-slate-200 bg-white p-6 shadow-xl animate-in fade-in zoom-in-95 duration-150">
              {/* Modal Header */}
              <div className="flex items-center justify-between pb-4 border-b border-slate-100">
                <div className="flex items-center gap-2">
                  <div className="p-2 bg-blue-50 text-blue-600 rounded-xl">
                    <Truck className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="text-base font-bold text-slate-900">Assign Courier to Parcel</h3>
                    <p className="text-xs text-slate-500 font-mono mt-0.5">{selectedParcel.trackingNumber}</p>
                  </div>
                </div>
                <button
                  onClick={handleCloseAssignModal}
                  disabled={isAssigning}
                  className="p-1 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer disabled:opacity-50"
                  aria-label="Close modal"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              {/* Modal Body */}
              <div className="space-y-4 pt-4">
                {/* Modal Alert Banner */}
                {assignmentError && (
                  <div className="rounded-xl border border-red-200 bg-red-50 p-3 text-xs text-red-700 flex items-start gap-2">
                    <ShieldAlert className="w-4 h-4 text-red-600 shrink-0 mt-0.5" />
                    <div className="flex-1 font-medium">{assignmentError}</div>
                  </div>
                )}

                {/* Selected Parcel Summary Card */}
                <div className="rounded-xl border border-slate-200/80 bg-slate-50/60 p-3.5 space-y-2 text-xs">
                  <div className="flex justify-between items-center">
                    <span className="text-slate-500 font-medium">Route:</span>
                    <span className="font-semibold text-slate-800">
                      {selectedParcel.originZoneCode || 'Origin'} → {selectedParcel.destinationZoneCode || 'Destination'}
                    </span>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-slate-500 font-medium">Recipient:</span>
                    <span className="font-semibold text-slate-800 truncate max-w-[200px]">
                      {selectedParcel.recipientName} ({selectedParcel.recipientPhone})
                    </span>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-slate-500 font-medium">Dropoff Address:</span>
                    <span className="font-semibold text-slate-800 truncate max-w-[240px]" title={selectedParcel.dropoffAddress}>
                      {selectedParcel.dropoffAddress}
                    </span>
                  </div>
                  <div className="flex justify-between items-center pt-1 border-t border-slate-200/60 text-slate-600">
                    <span>
                      Specs: <strong>{Number(selectedParcel.weightKg || 0).toFixed(1)} kg</strong> · {selectedParcel.parcelSize}
                    </span>
                    <span className="font-bold text-slate-900">
                      Fee: Rs. {Number(selectedParcel.estimatedFee || 0).toFixed(2)}
                    </span>
                  </div>
                </div>

                {/* Courier Selection Form */}
                <div className="space-y-2">
                  <label htmlFor="courier-select" className="block text-xs font-bold text-slate-700 uppercase tracking-wider">
                    Select Available Courier: <span className="text-rose-500">*</span>
                  </label>

                  {isLoadingModalCouriers ? (
                    <div className="py-8 flex flex-col items-center justify-center text-slate-400 text-xs">
                      <Spinner size="sm" />
                      <span className="mt-2">Loading available couriers for this zone...</span>
                    </div>
                  ) : modalCouriers.length === 0 ? (
                    <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-xs text-amber-800 text-center">
                      <AlertTriangle className="w-5 h-5 text-amber-600 mx-auto mb-1.5" />
                      <p className="font-bold">No active couriers available in this zone.</p>
                      <p className="mt-1 text-amber-700">All couriers are either off-duty or have reached capacity.</p>
                    </div>
                  ) : (
                    <div className="space-y-1.5 max-h-56 overflow-y-auto pr-1">
                      {modalCouriers.map((courier) => {
                        const isSelected = selectedCourierId === courier.courierId;
                        const isAtCapacity =
                          !courier.canAssign || courier.activeDeliveryCount >= (courier.maxWorkload || 20);
                        const isUnavailable = !courier.isAvailable;
                        const isDisabled = isAtCapacity || isUnavailable || isAssigning;

                        let workloadColor = 'bg-emerald-50 text-emerald-700 border-emerald-200';
                        if (courier.activeDeliveryCount >= 15) {
                          workloadColor = 'bg-amber-50 text-amber-700 border-amber-200';
                        }
                        if (isAtCapacity) {
                          workloadColor = 'bg-rose-50 text-rose-700 border-rose-200';
                        }

                        return (
                          <div
                            key={courier.courierId}
                            onClick={() => {
                              if (!isDisabled) {
                                setSelectedCourierId(courier.courierId);
                                setAssignmentError(null);
                              }
                            }}
                            className={`p-3 rounded-xl border text-xs transition-all flex items-center justify-between ${
                              isDisabled
                                ? 'bg-slate-50 border-slate-200/60 opacity-60 cursor-not-allowed'
                                : isSelected
                                ? 'border-blue-500 bg-blue-50/40 ring-1 ring-blue-500 cursor-pointer shadow-2xs'
                                : 'border-slate-200 bg-white hover:border-slate-300 hover:bg-slate-50/60 cursor-pointer'
                            }`}
                          >
                            <div className="flex items-center gap-2.5 min-w-0">
                              <div
                                className={`w-4 h-4 rounded-full border flex items-center justify-center shrink-0 ${
                                  isSelected
                                    ? 'border-blue-600 bg-blue-600 text-white'
                                    : 'border-slate-300 bg-white'
                                }`}
                              >
                                {isSelected && <Check className="w-2.5 h-2.5 stroke-[3]" />}
                              </div>

                              <div className="min-w-0">
                                <p className="font-bold text-slate-900 truncate flex items-center gap-1.5">
                                  <span>{courier.fullName}</span>
                                  {isAtCapacity && (
                                    <span className="text-[10px] font-semibold text-rose-600 bg-rose-50 px-1.5 py-0.5 rounded border border-rose-200">
                                      Full
                                    </span>
                                  )}
                                </p>
                                <p className="text-[11px] text-slate-500 mt-0.5 truncate">
                                  {courier.vehicleType || 'Vehicle'} · {courier.licensePlate}
                                </p>
                              </div>
                            </div>

                            {/* Workload Indicator Badge */}
                            <div className="shrink-0 text-right">
                              <span
                                className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-bold border ${workloadColor}`}
                                title={`Active deliveries: ${courier.activeDeliveryCount} / max: ${courier.maxWorkload || 20}`}
                              >
                                {courier.activeDeliveryCount}/{courier.maxWorkload || 20} Active
                              </span>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>

                {/* Maximum Workload Policy Reminder */}
                <p className="text-[11px] text-slate-400 flex items-center gap-1.5 pt-1">
                  <ShieldAlert className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                  <span>Couriers have an authoritative limit of 20 active out-for-delivery parcels.</span>
                </p>
              </div>

              {/* Modal Footer */}
              <div className="pt-5 mt-4 border-t border-slate-100 flex items-center justify-end gap-2.5">
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={handleCloseAssignModal}
                  disabled={isAssigning}
                >
                  Cancel
                </Button>
                <Button
                  variant="primary"
                  size="sm"
                  onClick={() => void handleConfirmAssignment()}
                  loading={isAssigning}
                  disabled={!selectedCourierId || isAssigning || modalCouriers.length === 0}
                  leftIcon={<UserCheck className="w-4 h-4" />}
                >
                  Confirm Assignment
                </Button>
              </div>
            </div>
          </div>
        )}
      </div>
    </AppShell>
  );
}
