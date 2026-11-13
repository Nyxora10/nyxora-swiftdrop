'use client';

import React, { useState, useEffect, useCallback, useMemo, Suspense } from 'react';
import Link from 'next/link';
import AppShell from '@/components/layout/AppShell';
import { getCourierHistory } from '@/services/courierService';
import {
  CourierDeliveryHistoryItem,
  CourierHistoryResponse,
  ParcelStatus,
} from '@/types/courier';
import { useToast } from '@/components/layout/ToastContext';
import {
  History,
  CheckCircle2,
  AlertTriangle,
  ShieldAlert,
  Search,
  RefreshCw,
  Package,
  MapPin,
  Phone,
  Calendar,
  Camera,
  FileCheck2,
  X,
  ExternalLink,
  ArrowLeft,
  DollarSign,
  TrendingUp,
} from 'lucide-react';

function CourierHistoryContent() {
  const toast = useToast();

  const [historyData, setHistoryData] = useState<CourierHistoryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [activeTab, setActiveTab] = useState<'ALL' | 'DELIVERED' | 'FAILED' | 'RETURNED_TO_SENDER'>('ALL');
  const [searchQuery, setSearchQuery] = useState('');

  // Selected proof for image lightbox modal
  const [selectedProof, setSelectedProof] = useState<CourierDeliveryHistoryItem | null>(null);

  const fetchHistory = useCallback(async (isManualRefresh = false) => {
    if (isManualRefresh) setRefreshing(true);
    try {
      const data = await getCourierHistory();
      setHistoryData(data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to load delivery history.';
      toast.error(msg);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [toast]);

  useEffect(() => {
    fetchHistory();
  }, [fetchHistory]);

  // Filter items by status tab and search query
  const filteredItems = useMemo(() => {
    if (!historyData?.history) return [];

    return historyData.history.filter((item) => {
      // Tab filter
      if (activeTab !== 'ALL' && item.finalStatus !== activeTab) {
        return false;
      }

      // Search filter
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const matchesTracking = item.trackingNumber.toLowerCase().includes(q);
        const matchesRecipient = item.recipientName.toLowerCase().includes(q);
        const matchesAddress = item.dropoffAddress.toLowerCase().includes(q);
        const matchesNote = item.deliveryNote?.toLowerCase().includes(q) ?? false;
        return matchesTracking || matchesRecipient || matchesAddress || matchesNote;
      }

      return true;
    });
  }, [historyData, activeTab, searchQuery]);

  const successRate = useMemo(() => {
    if (!historyData || historyData.totalCompleted === 0) return 0;
    return Math.round((historyData.deliveredCount / historyData.totalCompleted) * 100);
  }, [historyData]);

  const formatTimestamp = (ts?: string | null) => {
    if (!ts) return 'N/A';
    try {
      const d = new Date(ts);
      return d.toLocaleString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return ts;
    }
  };

  const getStatusBadge = (status: ParcelStatus) => {
    switch (status) {
      case 'DELIVERED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
            <CheckCircle2 className="w-3 h-3 text-emerald-600" /> Delivered
          </span>
        );
      case 'FAILED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-rose-50 text-rose-700 border border-rose-200">
            <AlertTriangle className="w-3 h-3 text-rose-600" /> Delivery Failed
          </span>
        );
      case 'RETURNED_TO_SENDER':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-slate-100 text-slate-700 border border-slate-300">
            <ShieldAlert className="w-3 h-3 text-slate-600" /> Returned to Sender
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-slate-50 text-slate-600 border border-slate-200">
            {status}
          </span>
        );
    }
  };

  // Resolve proof photo full URL for browser rendering
  const getProofImageSrc = (url?: string | null) => {
    if (!url) return '';
    if (url.startsWith('http://') || url.startsWith('https://')) {
      return url;
    }
    // Relative local fallback URL (e.g. /api/v1/parcels/proofs/...)
    const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
    return `${baseUrl}${url}`;
  };

  return (
    <AppShell>
      <div className="space-y-6 pb-12 max-w-5xl mx-auto">
        {/* Header Section */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 bg-white p-5 sm:p-6 rounded-2xl border border-slate-200 shadow-2xs">
          <div className="flex items-center gap-3.5">
            <div className="w-12 h-12 rounded-2xl bg-indigo-50 border border-indigo-100 flex items-center justify-center text-indigo-600 shrink-0 shadow-2xs">
              <History className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-xl font-bold text-slate-900 tracking-tight">Delivery History</h1>
                <span className="text-[11px] font-bold px-2 py-0.5 rounded-full bg-indigo-50 text-indigo-700 border border-indigo-200">
                  Audit Log
                </span>
              </div>
              <p className="text-xs text-slate-500 mt-0.5">
                Past completed deliveries, verified digital signatures, and doorstep proof photos
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <Link
              href="/courier/runsheet"
              className="inline-flex items-center gap-1.5 px-3.5 py-2 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-xs font-semibold transition-colors"
            >
              <ArrowLeft className="w-3.5 h-3.5" />
              Active Runsheet
            </Link>

            <button
              type="button"
              onClick={() => fetchHistory(true)}
              disabled={refreshing}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 bg-white hover:bg-slate-50 text-slate-700 rounded-xl text-xs font-semibold border border-slate-200 transition-colors shadow-2xs disabled:opacity-50"
              title="Refresh delivery history"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${refreshing ? 'animate-spin' : ''}`} />
              <span className="hidden sm:inline">Refresh</span>
            </button>
          </div>
        </div>

        {/* KPI Metrics Row */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
          <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-2xs">
            <div className="flex items-center justify-between text-slate-400 mb-1">
              <span className="text-[11px] uppercase font-bold tracking-wider">Total Completed</span>
              <Package className="w-4 h-4 text-indigo-500" />
            </div>
            <p className="text-2xl font-bold text-slate-900">{historyData?.totalCompleted ?? 0}</p>
            <p className="text-[10px] text-slate-400 mt-0.5">Archived courier deliveries</p>
          </div>

          <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-2xs">
            <div className="flex items-center justify-between text-slate-400 mb-1">
              <span className="text-[11px] uppercase font-bold tracking-wider">Delivered</span>
              <CheckCircle2 className="w-4 h-4 text-emerald-500" />
            </div>
            <p className="text-2xl font-bold text-emerald-600">{historyData?.deliveredCount ?? 0}</p>
            <div className="flex items-center gap-1 text-[10px] text-emerald-700 font-semibold mt-0.5">
              <TrendingUp className="w-3 h-3" />
              <span>{successRate}% Success Rate</span>
            </div>
          </div>

          <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-2xs">
            <div className="flex items-center justify-between text-slate-400 mb-1">
              <span className="text-[11px] uppercase font-bold tracking-wider">Delivery Failed</span>
              <AlertTriangle className="w-4 h-4 text-rose-500" />
            </div>
            <p className="text-2xl font-bold text-rose-600">{historyData?.failedCount ?? 0}</p>
            <p className="text-[10px] text-slate-400 mt-0.5">Logged failure attempts</p>
          </div>

          <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-2xs">
            <div className="flex items-center justify-between text-slate-400 mb-1">
              <span className="text-[11px] uppercase font-bold tracking-wider">Returned</span>
              <ShieldAlert className="w-4 h-4 text-slate-500" />
            </div>
            <p className="text-2xl font-bold text-slate-700">{historyData?.returnedCount ?? 0}</p>
            <p className="text-[10px] text-slate-400 mt-0.5">Sent back to sender</p>
          </div>
        </div>

        {/* Search & Filter Bar */}
        <div className="bg-white p-3.5 sm:p-4 rounded-2xl border border-slate-200 shadow-2xs space-y-3">
          {/* Partition Tabs */}
          <div className="flex border-b border-slate-100 pb-2 overflow-x-auto gap-1">
            <button
              type="button"
              onClick={() => setActiveTab('ALL')}
              className={`px-3.5 py-1.5 rounded-xl text-xs font-bold transition-all shrink-0 ${
                activeTab === 'ALL'
                  ? 'bg-slate-900 text-white shadow-2xs'
                  : 'text-slate-500 hover:text-slate-900 hover:bg-slate-100'
              }`}
            >
              All ({historyData?.totalCompleted ?? 0})
            </button>

            <button
              type="button"
              onClick={() => setActiveTab('DELIVERED')}
              className={`px-3.5 py-1.5 rounded-xl text-xs font-bold transition-all shrink-0 ${
                activeTab === 'DELIVERED'
                  ? 'bg-emerald-600 text-white shadow-2xs'
                  : 'text-slate-500 hover:text-slate-900 hover:bg-slate-100'
              }`}
            >
              Delivered ({historyData?.deliveredCount ?? 0})
            </button>

            <button
              type="button"
              onClick={() => setActiveTab('FAILED')}
              className={`px-3.5 py-1.5 rounded-xl text-xs font-bold transition-all shrink-0 ${
                activeTab === 'FAILED'
                  ? 'bg-rose-600 text-white shadow-2xs'
                  : 'text-slate-500 hover:text-slate-900 hover:bg-slate-100'
              }`}
            >
              Failed ({historyData?.failedCount ?? 0})
            </button>

            <button
              type="button"
              onClick={() => setActiveTab('RETURNED_TO_SENDER')}
              className={`px-3.5 py-1.5 rounded-xl text-xs font-bold transition-all shrink-0 ${
                activeTab === 'RETURNED_TO_SENDER'
                  ? 'bg-slate-700 text-white shadow-2xs'
                  : 'text-slate-500 hover:text-slate-900 hover:bg-slate-100'
              }`}
            >
              Returned ({historyData?.returnedCount ?? 0})
            </button>
          </div>

          {/* Search Input */}
          <div className="relative">
            <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search by tracking #, recipient name, address, or note..."
              className="w-full pl-9 pr-4 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-800 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:bg-white transition-all"
            />
          </div>
        </div>

        {/* Content List */}
        {loading ? (
          <div className="space-y-3">
            {[1, 2, 3].map((i) => (
              <div key={i} className="bg-white p-5 rounded-2xl border border-slate-200 animate-pulse space-y-3">
                <div className="flex justify-between items-center">
                  <div className="h-4 w-32 bg-slate-200 rounded" />
                  <div className="h-4 w-20 bg-slate-200 rounded-full" />
                </div>
                <div className="h-3 w-48 bg-slate-100 rounded" />
                <div className="h-3 w-full bg-slate-100 rounded" />
              </div>
            ))}
          </div>
        ) : filteredItems.length === 0 ? (
          <div className="bg-white rounded-2xl border border-slate-200 p-12 text-center shadow-2xs">
            <div className="w-14 h-14 rounded-2xl bg-slate-50 border border-slate-100 flex items-center justify-center mx-auto mb-3 text-slate-400">
              <Package className="w-7 h-7" />
            </div>
            <h3 className="text-sm font-bold text-slate-800">No delivery records found</h3>
            <p className="text-xs text-slate-400 mt-1 max-w-sm mx-auto">
              {searchQuery
                ? 'No deliveries matched your search filter. Try clearing the search query.'
                : 'You have not completed any deliveries in this category yet.'}
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {filteredItems.map((item) => (
              <div
                key={item.parcelId}
                className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200 shadow-2xs hover:border-slate-300 transition-all space-y-3.5"
              >
                {/* Top Row: Tracking & Status */}
                <div className="flex items-center justify-between flex-wrap gap-2">
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-xs font-bold text-slate-900 bg-slate-100 px-2 py-1 rounded-md">
                      {item.trackingNumber}
                    </span>
                    <span className="text-[11px] text-slate-400 flex items-center gap-1">
                      <Calendar className="w-3 h-3" />
                      {formatTimestamp(item.completedAt)}
                    </span>
                  </div>

                  <div>{getStatusBadge(item.finalStatus)}</div>
                </div>

                {/* Recipient & Dropoff Info */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs bg-slate-50 p-3 rounded-xl border border-slate-100">
                  <div>
                    <span className="text-[10px] uppercase font-bold text-slate-400 tracking-wider">
                      Recipient
                    </span>
                    <p className="font-bold text-slate-800 text-sm mt-0.5">{item.recipientName}</p>
                    {item.recipientPhone && (
                      <a
                        href={`tel:${item.recipientPhone}`}
                        className="inline-flex items-center gap-1 text-[11px] text-indigo-600 hover:text-indigo-800 font-medium mt-0.5"
                      >
                        <Phone className="w-3 h-3" />
                        {item.recipientPhone}
                      </a>
                    )}
                  </div>

                  <div>
                    <span className="text-[10px] uppercase font-bold text-slate-400 tracking-wider">
                      Delivery Address
                    </span>
                    <p className="text-slate-700 font-medium mt-0.5 flex items-start gap-1">
                      <MapPin className="w-3.5 h-3.5 text-slate-400 shrink-0 mt-0.5" />
                      <span>
                        {item.dropoffAddress}
                        {item.destinationZoneName && (
                          <span className="text-[10px] text-slate-400 block">
                            ({item.destinationZoneName})
                          </span>
                        )}
                      </span>
                    </p>
                  </div>
                </div>

                {/* Package Metrics & Verification Proof Summary */}
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pt-1 text-xs">
                  <div className="flex items-center gap-4 text-slate-500 text-[11px]">
                    <span>
                      <strong className="text-slate-700">{item.weightKg}</strong> kg
                    </span>
                    <span>•</span>
                    <span>
                      Size: <strong className="text-slate-700">{item.parcelSize}</strong>
                    </span>
                    <span>•</span>
                    <span className="font-semibold text-emerald-700 flex items-center">
                      <DollarSign className="w-3 h-3" />
                      LKR {Number(item.estimatedFee).toFixed(2)}
                    </span>
                  </div>

                  {/* Proof Actions */}
                  <div className="flex items-center gap-2 flex-wrap">
                    {item.recipientSignatureName && (
                      <span className="inline-flex items-center gap-1 px-2.5 py-1 bg-indigo-50 border border-indigo-200 text-indigo-800 rounded-lg text-[11px] font-semibold">
                        <FileCheck2 className="w-3.5 h-3.5 text-indigo-600" />
                        Signed by: {item.recipientSignatureName}
                      </span>
                    )}

                    {item.proofPhotoUrl && (
                      <button
                        type="button"
                        onClick={() => setSelectedProof(item)}
                        className="inline-flex items-center gap-1 px-2.5 py-1 bg-emerald-50 hover:bg-emerald-100 border border-emerald-200 text-emerald-800 rounded-lg text-[11px] font-semibold transition-colors shadow-2xs"
                      >
                        <Camera className="w-3.5 h-3.5 text-emerald-600" />
                        View Proof Photo
                      </button>
                    )}

                    {item.failureReason && (
                      <span className="inline-flex items-center gap-1 px-2.5 py-1 bg-rose-50 border border-rose-200 text-rose-800 rounded-lg text-[11px] font-semibold">
                        <AlertTriangle className="w-3.5 h-3.5 text-rose-600" />
                        Reason: {item.failureReason.replace(/_/g, ' ')}
                      </span>
                    )}
                  </div>
                </div>

                {/* Delivery Note if present */}
                {item.deliveryNote && (
                  <div className="text-[11px] text-slate-600 bg-slate-100/70 p-2.5 rounded-lg border border-slate-200/60">
                    <span className="font-semibold text-slate-700">Courier Note:</span> {item.deliveryNote}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}

        {/* Proof Photo Lightbox Modal */}
        {selectedProof && (
          <div className="fixed inset-0 bg-slate-950/70 backdrop-blur-xs z-50 flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl max-w-lg w-full overflow-hidden shadow-2xl border border-slate-200 space-y-4 animate-in fade-in zoom-in-95 duration-150">
              {/* Modal Header */}
              <div className="p-4 border-b border-slate-100 flex items-center justify-between">
                <div>
                  <h3 className="font-bold text-slate-900 text-sm">Delivery Proof Verification</h3>
                  <p className="text-[11px] text-slate-500 font-mono">
                    {selectedProof.trackingNumber} • {selectedProof.recipientName}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setSelectedProof(null)}
                  className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              {/* Modal Body / Image */}
              <div className="p-4 pt-0 space-y-3">
                <div className="relative rounded-xl overflow-hidden bg-slate-900 border border-slate-200 flex items-center justify-center min-h-[260px]">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src={getProofImageSrc(selectedProof.proofPhotoUrl)}
                    alt={`Proof for ${selectedProof.trackingNumber}`}
                    className="max-h-[380px] w-full object-contain"
                  />
                </div>

                {/* Details Footer */}
                <div className="bg-slate-50 p-3 rounded-xl border border-slate-100 text-xs space-y-1">
                  {selectedProof.recipientSignatureName && (
                    <div className="flex justify-between">
                      <span className="text-slate-400">Recipient Signature:</span>
                      <span className="font-semibold text-slate-800">
                        {selectedProof.recipientSignatureName}
                      </span>
                    </div>
                  )}

                  <div className="flex justify-between">
                    <span className="text-slate-400">Completed At:</span>
                    <span className="font-medium text-slate-700">
                      {formatTimestamp(selectedProof.completedAt)}
                    </span>
                  </div>

                  {selectedProof.deliveryNote && (
                    <div className="pt-1 text-slate-600 border-t border-slate-200/60">
                      <span className="text-slate-400 block text-[10px] uppercase font-bold">Note:</span>
                      {selectedProof.deliveryNote}
                    </div>
                  )}
                </div>
              </div>

              {/* Modal Actions */}
              <div className="p-4 border-t border-slate-100 bg-slate-50 flex items-center justify-between">
                <a
                  href={getProofImageSrc(selectedProof.proofPhotoUrl)}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1.5 text-xs font-semibold text-indigo-600 hover:text-indigo-800"
                >
                  Open Full Image <ExternalLink className="w-3.5 h-3.5" />
                </a>

                <button
                  type="button"
                  onClick={() => setSelectedProof(null)}
                  className="px-4 py-2 bg-slate-900 hover:bg-slate-800 text-white rounded-xl text-xs font-semibold shadow-2xs transition-colors"
                >
                  Close Preview
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </AppShell>
  );
}

export default function CourierHistoryPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
          <div className="flex flex-col items-center gap-3">
            <div className="w-8 h-8 border-3 border-indigo-600 border-t-transparent rounded-full animate-spin" />
            <p className="text-xs font-semibold text-slate-500">Loading delivery history...</p>
          </div>
        </div>
      }
    >
      <CourierHistoryContent />
    </Suspense>
  );
}

