'use client';

import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import {
  Package,
  TrendingUp,
  Clock,
  CheckCircle2,
  UploadCloud,
  Key,
  RefreshCw,
  DollarSign,
  Copy,
  Check,
  ShieldAlert,
  ArrowRight,
} from 'lucide-react';
import AppShell from '@/components/layout/AppShell';
import MerchantOrdersTable from '@/components/merchant/MerchantOrdersTable';
import { useMerchantOrders } from '@/hooks/useMerchantOrders';
import { api } from '@/lib/api';
import { apiErrorMessage, formatLkr } from '@/lib/merchant/format';
import type { MerchantProfile } from '@/types/merchant';

const RECENT_ORDER_COUNT = 10;

export default function MerchantDashboardPage() {
  const [profile, setProfile] = useState<MerchantProfile | null>(null);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [profileReloadKey, setProfileReloadKey] = useState(0);
  const [rotateError, setRotateError] = useState<string | null>(null);
  const {
    data: history,
    loading: ordersLoading,
    error: ordersError,
    reload: reloadOrders,
  } = useMerchantOrders({ page: 0, size: RECENT_ORDER_COUNT });

  // Credentials Modal
  const [showCredModal, setShowCredModal] = useState(false);
  const [rotatingSecret, setRotatingSecret] = useState(false);
  const [newClientSecret, setNewClientSecret] = useState<string | null>(null);
  const [regeneratingWebhookSecret, setRegeneratingWebhookSecret] = useState(false);
  // Shown once, straight from the regeneration response; never re-fetchable from the profile.
  const [newWebhookSecret, setNewWebhookSecret] = useState<string | null>(null);
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  // Profile and orders load independently, so one failing never hides the other.
  useEffect(() => {
    let cancelled = false;
    api
      .get<MerchantProfile>('/api/v1/auth/merchant/profile')
      .then((res) => {
        if (cancelled) return;
        setProfile(res.data);
        setProfileError(null);
      })
      .catch((err) => {
        if (!cancelled) setProfileError(apiErrorMessage(err, 'Could not load your merchant profile and API credentials.'));
      });
    return () => {
      cancelled = true;
    };
  }, [profileReloadKey]);

  const refreshDashboard = useCallback(() => {
    setProfileReloadKey((k) => k + 1);
    reloadOrders();
  }, [reloadOrders]);

  const loadErrors = [profileError, ordersError].filter((e): e is string => Boolean(e));
  const orders = history?.orders?.content ?? [];

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

  // Handle webhook HMAC signing secret regeneration
  const handleRegenerateWebhookSecret = async () => {
    if (!confirm('Regenerate your webhook signing secret? Webhooks will be signed with the new secret immediately, so update your verification code right away.')) {
      return;
    }
    setRegeneratingWebhookSecret(true);
    setRotateError(null);
    try {
      const res = await api.put<MerchantProfile & { newWebhookSecret?: string }>(
        '/api/v1/auth/merchant/webhook',
        { regenerateSecret: true },
      );
      const { newWebhookSecret: revealed, ...updatedProfile } = res.data;
      setProfile(updatedProfile);
      setNewWebhookSecret(revealed ?? null);
      if (!revealed) {
        setRotateError('The webhook secret was regenerated, but the server did not return it. Regenerate again to obtain a usable secret.');
      }
    } catch (err) {
      setNewWebhookSecret(null);
      setRotateError(apiErrorMessage(err, 'Webhook secret regeneration failed. Your existing webhook secret is unchanged.'));
    } finally {
      setRegeneratingWebhookSecret(false);
    }
  };

  const closeCredModal = () => {
    // Drop one-time secrets from memory once the merchant leaves the modal.
    setNewClientSecret(null);
    setNewWebhookSecret(null);
    setShowCredModal(false);
  };

  const copyToClipboard = (text: string, keyName: string) => {
    navigator.clipboard.writeText(text);
    setCopiedKey(keyName);
    setTimeout(() => setCopiedKey(null), 2500);
  };

  // Lifetime figures straight from the server, so they cover every order, not just this page.
  const total = history?.totalOrders ?? 0;
  const delivered = history?.deliveredCount ?? 0;
  const metrics = {
    total,
    delivered,
    inTransit: history?.inTransitCount ?? 0,
    rate: total > 0 ? Math.round((delivered / total) * 100) : 0,
    savings: history?.totalSavings ?? 0,
  };

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
              <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">In Transit</span>
              <div className="p-2 rounded-lg bg-amber-50 text-amber-600">
                <Clock className="w-5 h-5" />
              </div>
            </div>
            <div className="mt-4 flex items-baseline gap-2">
              <span className="text-3xl font-extrabold text-slate-900">{metrics.inTransit}</span>
              <span className="text-xs text-amber-600 font-medium">on route</span>
            </div>
            <p className="mt-1 text-xs text-slate-400">In transit or out for delivery</p>
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
              <span className="text-3xl font-extrabold text-indigo-600">{formatLkr(metrics.savings)}</span>
            </div>
            <p className="mt-1 text-xs text-indigo-500 font-medium">Total discount across all orders</p>
          </div>
        </div>

        {/* Recent Orders */}
        <div className="bg-white rounded-2xl border border-slate-200 shadow-xs overflow-hidden">
          <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between gap-3">
            <h2 className="text-base font-bold text-slate-900">Recent Shipments</h2>
            <div className="flex items-center gap-2">
              <button
                onClick={refreshDashboard}
                disabled={ordersLoading}
                className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-100 transition-all"
                title="Refresh"
                aria-label="Refresh"
              >
                <RefreshCw className={`w-4 h-4 ${ordersLoading ? 'animate-spin text-blue-600' : ''}`} />
              </button>
              <Link
                href="/merchant/orders"
                className="inline-flex items-center gap-1 text-xs font-semibold text-blue-600 hover:text-blue-800"
              >
                View all orders <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>
          </div>
          <MerchantOrdersTable
            orders={orders}
            loading={ordersLoading}
            emptyMessage="No orders yet. Upload a batch or connect your store through the API to book your first parcel."
          />
          {!ordersLoading && metrics.total > orders.length && (
            <div className="px-6 py-3 border-t border-slate-100 text-xs text-slate-500">
              Showing the latest {orders.length} of {metrics.total} orders.
            </div>
          )}
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
                  onClick={closeCredModal}
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

              {/* Webhook Secret One-Time Reveal */}
              {newWebhookSecret && (
                <div className="p-4 bg-emerald-50 border border-emerald-200 rounded-2xl space-y-2">
                  <div className="flex items-center gap-2 text-emerald-800 font-bold text-xs uppercase">
                    <CheckCircle2 className="w-4 h-4" /> New Webhook Signing Secret Generated
                  </div>
                  <p className="text-xs text-emerald-700">
                    Use this secret to verify webhook HMAC signatures. It will never be displayed again:
                  </p>
                  <div className="flex items-center gap-2">
                    <input
                      type="text"
                      readOnly
                      value={newWebhookSecret}
                      className="flex-1 bg-white border border-emerald-300 rounded-xl px-3 py-2 text-xs font-mono text-emerald-900 font-bold"
                    />
                    <button
                      onClick={() => copyToClipboard(newWebhookSecret, 'webhookSecret')}
                      className="p-2 bg-emerald-600 text-white hover:bg-emerald-700 rounded-xl transition-all"
                      title="Copy Webhook Secret"
                    >
                      {copiedKey === 'webhookSecret' ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                    </button>
                  </div>
                </div>
              )}

              {/* Rotate Actions */}
              <div className="pt-2 flex flex-col sm:flex-row items-center justify-between gap-3">
                <div className="w-full sm:w-auto flex flex-col sm:flex-row gap-2">
                  <button
                    onClick={handleRotateSecret}
                    disabled={rotatingSecret}
                    className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-4 py-2.5 text-xs font-bold text-rose-700 bg-rose-50 hover:bg-rose-100 border border-rose-200 rounded-xl transition-all"
                  >
                    <RefreshCw className={`w-3.5 h-3.5 ${rotatingSecret ? 'animate-spin' : ''}`} />
                    {rotatingSecret ? 'Rotating Key...' : 'Rotate Client Secret'}
                  </button>
                  <button
                    onClick={handleRegenerateWebhookSecret}
                    disabled={regeneratingWebhookSecret}
                    className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-4 py-2.5 text-xs font-bold text-amber-800 bg-amber-50 hover:bg-amber-100 border border-amber-200 rounded-xl transition-all"
                  >
                    <RefreshCw className={`w-3.5 h-3.5 ${regeneratingWebhookSecret ? 'animate-spin' : ''}`} />
                    {regeneratingWebhookSecret ? 'Regenerating...' : 'Regenerate Webhook Secret'}
                  </button>
                </div>

                <button
                  onClick={closeCredModal}
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
