'use client';

import React, { useState, useEffect, useMemo, useCallback } from 'react';
import Link from 'next/link';
import {
  Package,
  TrendingUp,
  Clock,
  CheckCircle2,
  UploadCloud,
  Key,
  RefreshCw,
  Search,
  ExternalLink,
  DollarSign,
  Copy,
  Check,
  ShieldAlert,
} from 'lucide-react';
import AppShell from '@/components/layout/AppShell';
import { api } from '@/lib/api';

// Mirrors parcel-service OrderSummaryDto (GET /api/v1/parcels/merchant/orders → orders.content[])
interface MerchantOrder {
  parcelId: string;
  trackingNumber: string;
  recipientName: string;
  recipientPhone: string;
  dropoffAddress: string;
  originZoneName?: string;
  destinationZoneName?: string;
  weightKg: number;
  parcelSize: 'SMALL' | 'MEDIUM' | 'LARGE';
  estimatedFee: number;
  currentStatus: 'CREATED' | 'ASSIGNED' | 'PICKED_UP' | 'IN_TRANSIT' | 'OUT_FOR_DELIVERY' | 'DELIVERED' | 'FAILED' | 'RETURNED_TO_SENDER';
  createdAt: string;
}

// Mirrors parcel-service MerchantOrderHistoryResponse lifetime counters
interface MerchantOrderSummary {
  totalOrders: number;
  inTransitCount: number;
  deliveredCount: number;
  failedCount: number;
}

const ORDER_PAGE_SIZE = 50; // parcel-service caps page size at 50

function apiErrorMessage(err: unknown, fallback: string): string {
  const data = (err as { response?: { data?: { detail?: string; message?: string } } })?.response?.data;
  return data?.detail || data?.message || fallback;
}

interface MerchantProfile {
  merchantId: string;
  businessName: string;
  registrationNumber: string;
  oauthClientId: string;
  webhookUrl: string;
  webhookSecret: string;
}

interface DashboardSnapshot {
  profile: MerchantProfile | null;
  orders: MerchantOrder[];
  summary: MerchantOrderSummary | null;
  errors: string[];
}

// Loads profile and orders in parallel; each failure becomes a visible error instead of fake data.
async function fetchDashboardSnapshot(): Promise<DashboardSnapshot> {
  const [profileResult, ordersResult] = await Promise.allSettled([
    api.get<MerchantProfile>('/api/v1/auth/merchant/profile'),
    api.get('/api/v1/parcels/merchant/orders', { params: { page: 0, size: ORDER_PAGE_SIZE } }),
  ]);

  const snapshot: DashboardSnapshot = { profile: null, orders: [], summary: null, errors: [] };

  if (profileResult.status === 'fulfilled') {
    snapshot.profile = profileResult.value.data;
  } else {
    snapshot.errors.push(apiErrorMessage(profileResult.reason, 'Could not load your merchant profile and API credentials.'));
  }

  if (ordersResult.status === 'fulfilled') {
    const data = ordersResult.value.data;
    snapshot.orders = data?.orders?.content ?? [];
    snapshot.summary = {
      totalOrders: data?.totalOrders ?? 0,
      inTransitCount: data?.inTransitCount ?? 0,
      deliveredCount: data?.deliveredCount ?? 0,
      failedCount: data?.failedCount ?? 0,
    };
  } else {
    snapshot.errors.push(apiErrorMessage(ordersResult.reason, 'Could not load your orders.'));
  }

  return snapshot;
}

export default function MerchantDashboardPage() {
  const [orders, setOrders] = useState<MerchantOrder[]>([]);
  const [summary, setSummary] = useState<MerchantOrderSummary | null>(null);
  const [profile, setProfile] = useState<MerchantProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadErrors, setLoadErrors] = useState<string[]>([]);
  const [rotateError, setRotateError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState('');

  // Credentials Modal
  const [showCredModal, setShowCredModal] = useState(false);
  const [rotatingSecret, setRotatingSecret] = useState(false);
  const [newClientSecret, setNewClientSecret] = useState<string | null>(null);
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  const applySnapshot = useCallback((snapshot: DashboardSnapshot) => {
    setProfile(snapshot.profile);
    setOrders(snapshot.orders);
    setSummary(snapshot.summary);
    setLoadErrors(snapshot.errors);
    setLoading(false);
  }, []);

  useEffect(() => {
    let cancelled = false;
    fetchDashboardSnapshot().then((snapshot) => {
      if (!cancelled) applySnapshot(snapshot);
    });
    return () => {
      cancelled = true;
    };
  }, [applySnapshot]);

  const refreshDashboard = () => {
    setLoading(true);
    fetchDashboardSnapshot().then(applySnapshot);
  };

  // Handle OAuth Client Secret Rotation
  const handleRotateSecret = async () => {
    if (!confirm('Are you sure you want to rotate your OAuth 2.0 client secret? Your existing secret will be invalidated immediately.')) {
      return;
    }
    setRotatingSecret(true);
    setRotateError(null);
    try {
      const res = await api.post('/api/v1/auth/merchant/credentials/rotate');
      setNewClientSecret(res.data.oauthClientSecret);
    } catch (err) {
      // Never show a made-up secret: the old secret is still valid if rotation failed.
      setNewClientSecret(null);
      setRotateError(apiErrorMessage(err, 'Secret rotation failed. Your existing client secret is unchanged.'));
    } finally {
      setRotatingSecret(false);
    }
  };

  const copyToClipboard = (text: string, keyName: string) => {
    navigator.clipboard.writeText(text);
    setCopiedKey(keyName);
    setTimeout(() => setCopiedKey(null), 2500);
  };

  // Metrics Calculations
  // Lifetime counters come from the server; savings are estimated from the loaded page of orders.
  const metrics = useMemo(() => {
    const total = summary?.totalOrders ?? 0;
    const delivered = summary?.deliveredCount ?? 0;
    const inTransit = summary?.inTransitCount ?? 0;
    const failed = summary?.failedCount ?? 0;
    const rate = total > 0 ? Math.round((delivered / total) * 100) : 0;
    const totalFee = orders.reduce((sum, o) => sum + (Number(o.estimatedFee) || 0), 0);
    // 15% corporate discount saved = totalFee / 0.85 * 0.15
    const corporateSavings = totalFee > 0 ? Math.round((totalFee / 0.85) * 0.15) : 0;

    return { total, delivered, inTransit, failed, rate, totalFee, corporateSavings };
  }, [orders, summary]);

  // Filtered Orders
  const filteredOrders = useMemo(() => {
    return orders.filter((o) => {
      const matchesStatus =
        statusFilter === 'ALL'
          ? true
          : statusFilter === 'ACTIVE'
          ? ['CREATED', 'ASSIGNED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY'].includes(o.currentStatus)
          : o.currentStatus === statusFilter;

      const q = searchQuery.toLowerCase().trim();
      const matchesSearch =
        !q ||
        o.trackingNumber.toLowerCase().includes(q) ||
        o.recipientName.toLowerCase().includes(q) ||
        o.recipientPhone.includes(q) ||
        (o.destinationZoneName && o.destinationZoneName.toLowerCase().includes(q));

      return matchesStatus && matchesSearch;
    });
  }, [orders, statusFilter, searchQuery]);

  return (
    <AppShell>
      <div className="space-y-6">
        {/* Header with Title and Action Buttons */}
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 bg-white p-6 rounded-2xl border border-slate-200 shadow-xs">
          <div>
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-blue-600 flex items-center justify-center text-white shadow-md shadow-blue-500/20">
                <Package className="w-6 h-6" />
              </div>
              <div>
                <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Merchant B2B Dashboard</h1>
                <p className="text-sm text-slate-500">
                  {profile?.businessName || 'Merchant Partner'} &bull; Registration: {profile?.registrationNumber || 'Verified'}
                </p>
              </div>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <button
              onClick={() => setShowCredModal(true)}
              className="inline-flex items-center gap-2 px-4 py-2.5 text-sm font-semibold text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-xl transition-all"
            >
              <Key className="w-4 h-4 text-slate-600" />
              API Credentials
            </button>
            <Link
              href="/merchant/bulk-upload"
              className="inline-flex items-center gap-2 px-4 py-2.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-xl shadow-md shadow-blue-500/20 transition-all"
            >
              <UploadCloud className="w-4 h-4" />
              Bulk CSV Ingestion
            </Link>
          </div>
        </div>

        {loadErrors.length > 0 && (
          <div role="alert" className="flex items-start gap-3 p-4 bg-rose-50 border border-rose-200 rounded-2xl text-sm text-rose-800">
            <ShieldAlert className="w-5 h-5 shrink-0 mt-0.5" />
            <div className="flex-1 space-y-1">
              {loadErrors.map((msg) => (
                <p key={msg}>{msg}</p>
              ))}
            </div>
            <button
              onClick={refreshDashboard}
              className="text-xs font-semibold text-rose-700 hover:text-rose-900 underline"
            >
              Retry
            </button>
          </div>
        )}

        {/* Top Metric Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Total Bookings</span>
              <div className="p-2 rounded-lg bg-blue-50 text-blue-600">
                <TrendingUp className="w-5 h-5" />
              </div>
            </div>
            <div className="mt-4 flex items-baseline gap-2">
              <span className="text-3xl font-extrabold text-slate-900">{metrics.total}</span>
              <span className="text-xs text-slate-500">parcels</span>
            </div>
            <p className="mt-1 text-xs text-slate-400">Lifetime volume processed</p>
          </div>

          <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">In Transit / Active</span>
              <div className="p-2 rounded-lg bg-amber-50 text-amber-600">
                <Clock className="w-5 h-5" />
              </div>
            </div>
            <div className="mt-4 flex items-baseline gap-2">
              <span className="text-3xl font-extrabold text-slate-900">{metrics.inTransit}</span>
              <span className="text-xs text-amber-600 font-medium">on route</span>
            </div>
            <p className="mt-1 text-xs text-slate-400">Pickups & deliveries active</p>
          </div>

          <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Delivery Success Rate</span>
              <div className="p-2 rounded-lg bg-emerald-50 text-emerald-600">
                <CheckCircle2 className="w-5 h-5" />
              </div>
            </div>
            <div className="mt-4 flex items-baseline gap-2">
              <span className="text-3xl font-extrabold text-slate-900">{metrics.rate}%</span>
              <span className="text-xs text-emerald-600 font-medium">
                {metrics.delivered}/{metrics.total} completed
              </span>
            </div>
            <p className="mt-1 text-xs text-slate-400">Strict SLA fulfillment</p>
          </div>

          <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">15% Contracted Savings</span>
              <div className="p-2 rounded-lg bg-indigo-50 text-indigo-600">
                <DollarSign className="w-5 h-5" />
              </div>
            </div>
            <div className="mt-4 flex items-baseline gap-1">
              <span className="text-xs font-bold text-slate-500">LKR</span>
              <span className="text-3xl font-extrabold text-indigo-600">{metrics.corporateSavings.toLocaleString()}</span>
            </div>
            <p className="mt-1 text-xs text-indigo-500 font-medium">
              Saved via B2B Corporate Tier{summary && summary.totalOrders > orders.length ? ` (latest ${orders.length} orders)` : ''}
            </p>
          </div>
        </div>

        {/* Filter and Search Bar */}
        <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 bg-white p-4 rounded-2xl border border-slate-200 shadow-xs">
          <div className="flex flex-wrap items-center gap-1.5">
            {[
              { label: 'All Orders', value: 'ALL' },
              { label: 'Active', value: 'ACTIVE' },
              { label: 'Created', value: 'CREATED' },
              { label: 'In Transit', value: 'IN_TRANSIT' },
              { label: 'Delivered', value: 'DELIVERED' },
              { label: 'Failed', value: 'FAILED' },
            ].map((tab) => (
              <button
                key={tab.value}
                onClick={() => setStatusFilter(tab.value)}
                className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                  statusFilter === tab.value
                    ? 'bg-blue-600 text-white shadow-xs'
                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>

          <div className="relative min-w-[260px]">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Search tracking, recipient..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-4 py-2 text-xs bg-slate-50 border border-slate-200 rounded-xl focus:outline-hidden focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all text-slate-800"
            />
          </div>
        </div>

        {/* Orders Table */}
        <div className="bg-white rounded-2xl border border-slate-200 shadow-xs overflow-hidden">
          <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between">
            <h2 className="text-base font-bold text-slate-900">Shipments & Delivery Matrix</h2>
            <button
              onClick={refreshDashboard}
              disabled={loading}
              className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-100 transition-all"
              title="Refresh"
            >
              <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-blue-600' : ''}`} />
            </button>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-slate-50/75 border-b border-slate-100 text-slate-500 text-xs uppercase font-semibold">
                  <th className="py-3 px-6">Tracking Number</th>
                  <th className="py-3 px-6">Recipient</th>
                  <th className="py-3 px-6">Destination</th>
                  <th className="py-3 px-4">Size & Weight</th>
                  <th className="py-3 px-4">Discounted Fee</th>
                  <th className="py-3 px-6">Status</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-sm">
                {loading ? (
                  <tr>
                    <td colSpan={7} className="py-12 text-center text-slate-400">
                      <RefreshCw className="w-6 h-6 animate-spin mx-auto text-blue-600 mb-2" />
                      Loading merchant parcels...
                    </td>
                  </tr>
                ) : filteredOrders.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="py-12 text-center text-slate-400">
                      No parcels match the selected status or search term.
                    </td>
                  </tr>
                ) : (
                  filteredOrders.map((order) => {
                    const statusColors: Record<string, string> = {
                      CREATED: 'bg-slate-100 text-slate-700 border-slate-200',
                      ASSIGNED: 'bg-indigo-50 text-indigo-700 border-indigo-200',
                      PICKED_UP: 'bg-blue-50 text-blue-700 border-blue-200',
                      IN_TRANSIT: 'bg-amber-50 text-amber-700 border-amber-200',
                      OUT_FOR_DELIVERY: 'bg-purple-50 text-purple-700 border-purple-200',
                      DELIVERED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
                      FAILED: 'bg-rose-50 text-rose-700 border-rose-200',
                      RETURNED_TO_SENDER: 'bg-orange-50 text-orange-700 border-orange-200',
                    };

                    return (
                      <tr key={order.parcelId} className="hover:bg-slate-50/60 transition-colors">
                        <td className="py-4 px-6 font-mono font-bold text-blue-600">
                          {order.trackingNumber}
                        </td>
                        <td className="py-4 px-6">
                          <div className="font-semibold text-slate-900">{order.recipientName}</div>
                          <div className="text-xs text-slate-400 font-mono">{order.recipientPhone}</div>
                        </td>
                        <td className="py-4 px-6">
                          <div className="text-slate-800 text-xs font-medium truncate max-w-[200px]" title={order.dropoffAddress}>
                            {order.dropoffAddress}
                          </div>
                          <div className="text-2xs text-slate-400">{order.destinationZoneName || 'Island-wide'}</div>
                        </td>
                        <td className="py-4 px-4 text-xs text-slate-600">
                          <span className="font-medium">{order.weightKg} kg</span> &bull;{' '}
                          <span className="px-1.5 py-0.5 rounded-md bg-slate-100 text-2xs font-semibold">
                            {order.parcelSize}
                          </span>
                        </td>
                        <td className="py-4 px-4">
                          <div className="font-bold text-slate-900">
                            LKR {Number(order.estimatedFee ?? 0).toFixed(2)}
                          </div>
                          <div className="text-2xs text-emerald-600 font-medium">15% saved</div>
                        </td>
                        <td className="py-4 px-6">
                          <span
                            className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold border ${
                              statusColors[order.currentStatus] || 'bg-slate-100 text-slate-700 border-slate-200'
                            }`}
                          >
                            {order.currentStatus.replace(/_/g, ' ')}
                          </span>
                        </td>
                        <td className="py-4 px-4 text-right">
                          <Link
                            href={`/track/${order.trackingNumber}`}
                            target="_blank"
                            className="inline-flex items-center gap-1 text-xs font-semibold text-blue-600 hover:text-blue-800 transition-colors"
                          >
                            Track <ExternalLink className="w-3.5 h-3.5" />
                          </Link>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* API Credentials & Webhook Modal */}
        {showCredModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs p-4">
            <div className="bg-white rounded-3xl max-w-xl w-full p-6 sm:p-8 shadow-2xl border border-slate-100 space-y-6">
              <div className="flex items-center justify-between pb-4 border-b border-slate-100">
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 rounded-xl bg-blue-100 text-blue-700 flex items-center justify-center">
                    <Key className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="text-lg font-bold text-slate-900">Machine-to-Machine Credentials</h3>
                    <p className="text-xs text-slate-500">OAuth 2.0 client_credentials for automated server APIs</p>
                  </div>
                </div>
                <button
                  onClick={() => setShowCredModal(false)}
                  className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100"
                >
                  &times;
                </button>
              </div>

              {/* OAuth Client ID */}
              <div className="space-y-1.5">
                <label className="text-xs font-bold text-slate-700 uppercase tracking-wider">OAuth 2.0 Client ID</label>
                <div className="flex items-center gap-2">
                  <input
                    type="text"
                    readOnly
                    value={profile?.oauthClientId || 'Unavailable'}
                    className="flex-1 bg-slate-50 border border-slate-200 rounded-xl px-3 py-2 text-xs font-mono text-slate-800"
                  />
                  <button
                    onClick={() => profile?.oauthClientId && copyToClipboard(profile.oauthClientId, 'clientId')}
                    disabled={!profile?.oauthClientId}
                    className="p-2 text-slate-600 bg-slate-100 hover:bg-slate-200 rounded-xl transition-all"
                    title="Copy Client ID"
                  >
                    {copiedKey === 'clientId' ? <Check className="w-4 h-4 text-emerald-600" /> : <Copy className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              {/* Active Webhook URL & Masked Secret */}
              <div className="space-y-3 bg-slate-50 p-4 rounded-2xl border border-slate-200">
                <div>
                  <span className="text-xs font-bold text-slate-700 uppercase tracking-wider">Outbound Webhook URL</span>
                  <p className="text-xs font-mono text-slate-600 mt-0.5 truncate">
                    {profile?.webhookUrl || 'Not configured'}
                  </p>
                </div>
                <div className="flex items-center justify-between">
                  <div>
                    <span className="text-xs font-bold text-slate-700 uppercase tracking-wider">HMAC Signing Secret</span>
                    <p className="text-xs font-mono text-slate-600 mt-0.5">{profile?.webhookSecret || 'whsec_****'}</p>
                  </div>
                  <Link
                    href="/merchant/webhooks"
                    className="text-xs font-semibold text-blue-600 hover:text-blue-800 inline-flex items-center gap-1"
                  >
                    Configure Webhooks &rarr;
                  </Link>
                </div>
              </div>

              {rotateError && (
                <div role="alert" className="p-3 bg-rose-50 border border-rose-200 rounded-2xl text-xs text-rose-800">
                  {rotateError}
                </div>
              )}

              {/* Secret Rotation Alert Box */}
              {newClientSecret && (
                <div className="p-4 bg-emerald-50 border border-emerald-200 rounded-2xl space-y-2">
                  <div className="flex items-center gap-2 text-emerald-800 font-bold text-xs uppercase">
                    <CheckCircle2 className="w-4 h-4" /> New Client Secret Generated
                  </div>
                  <p className="text-xs text-emerald-700">
                    Store this secret securely. For security reasons, it will never be displayed again:
                  </p>
                  <div className="flex items-center gap-2">
                    <input
                      type="text"
                      readOnly
                      value={newClientSecret}
                      className="flex-1 bg-white border border-emerald-300 rounded-xl px-3 py-2 text-xs font-mono text-emerald-900 font-bold"
                    />
                    <button
                      onClick={() => copyToClipboard(newClientSecret, 'clientSecret')}
                      className="p-2 bg-emerald-600 text-white hover:bg-emerald-700 rounded-xl transition-all"
                    >
                      {copiedKey === 'clientSecret' ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                    </button>
                  </div>
                </div>
              )}

              {/* Rotate Action */}
              <div className="pt-2 flex flex-col sm:flex-row items-center justify-between gap-3">
                <button
                  onClick={handleRotateSecret}
                  disabled={rotatingSecret}
                  className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-4 py-2.5 text-xs font-bold text-rose-700 bg-rose-50 hover:bg-rose-100 border border-rose-200 rounded-xl transition-all"
                >
                  <RefreshCw className={`w-3.5 h-3.5 ${rotatingSecret ? 'animate-spin' : ''}`} />
                  {rotatingSecret ? 'Rotating Key...' : 'Rotate Client Secret'}
                </button>

                <button
                  onClick={() => setShowCredModal(false)}
                  className="w-full sm:w-auto px-5 py-2.5 text-xs font-bold text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-xl transition-all"
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </AppShell>
  );
}
