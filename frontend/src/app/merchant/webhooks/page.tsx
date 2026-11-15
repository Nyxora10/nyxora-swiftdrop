'use client';

import React, { useState, useEffect, useCallback } from 'react';
import AppShell from '@/components/layout/AppShell';
import { useToast } from '@/components/layout/ToastContext';
import api from '@/lib/api';
import {
  WebhookDeliveryLogItem,
  WebhookLogsPageResponse,
  WebhookDeliveryStatus,
  ManualRetryResponse,
  DemoWebhookEvent,
  DemoWebhookEventsResponse,
  MerchantProfileResponse,
} from '@/types/webhook';
import {
  Webhook,
  RefreshCw,
  RotateCcw,
  CheckCircle2,
  AlertCircle,
  Clock,
  XCircle,
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  Radio,
  Trash2,
  Copy,
  Check,
  Shield,
  Key,
  Info,
  SlidersHorizontal,
  Code2,
  Globe,
  Lock,
  AlertTriangle,
  Send,
} from 'lucide-react';

export default function MerchantWebhooksPage() {
  const toast = useToast();

  // Profile & Webhook Settings State
  const [profile, setProfile] = useState<MerchantProfileResponse | null>(null);
  const [loadingProfile, setLoadingProfile] = useState(true);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [urlInput, setUrlInput] = useState('');
  const [savingUrl, setSavingUrl] = useState(false);
  const [regeneratingSecret, setRegeneratingSecret] = useState(false);
  const [confirmRegenerateOpen, setConfirmRegenerateOpen] = useState(false);
  const [newlyGeneratedSecret, setNewlyGeneratedSecret] = useState<string | null>(null);
  const [copiedNewSecret, setCopiedNewSecret] = useState(false);

  // Delivery Logs State
  const [logs, setLogs] = useState<WebhookDeliveryLogItem[]>([]);
  const [loadingLogs, setLoadingLogs] = useState(true);
  const [logsError, setLogsError] = useState<string | null>(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [retryingLogId, setRetryingLogId] = useState<string | null>(null);
  const [refreshTrigger, setRefreshTrigger] = useState(0);

  // Demo Events State
  const [demoEvents, setDemoEvents] = useState<DemoWebhookEvent[]>([]);
  const [demoError, setDemoError] = useState<string | null>(null);
  const [pollingActive, setPollingActive] = useState(true);
  const [clearingEvents, setClearingEvents] = useState(false);
  const [expandedPayloadId, setExpandedPayloadId] = useState<string | null>(null);
  const [copiedPayloadId, setCopiedPayloadId] = useState<string | null>(null);
  const [showDeveloperDetails, setShowDeveloperDetails] = useState(false);

  // Fetch Merchant Profile
  useEffect(() => {
    let isCancelled = false;

    async function loadProfile() {
      try {
        setLoadingProfile(true);
        const res = await api.get<MerchantProfileResponse>('/api/v1/auth/merchant/profile');
        if (!isCancelled) {
          setProfile(res.data);
          setUrlInput(res.data.webhookUrl || '');
          setProfileError(null);
          setLoadingProfile(false);
        }
      } catch (err: unknown) {
        if (!isCancelled) {
          const error = err as { response?: { status?: number; data?: { message?: string } } };
          const status = error.response?.status;
          const msg = error.response?.data?.message;

          if (status === 403) {
            setProfileError('Access restricted. Only verified merchant accounts can manage notification settings.');
          } else {
            setProfileError(msg || 'Unable to load merchant notification settings.');
          }
          setLoadingProfile(false);
        }
      }
    }

    loadProfile();

    return () => {
      isCancelled = true;
    };
  }, [refreshTrigger]);

  // Fetch Delivery Logs
  useEffect(() => {
    let isCancelled = false;

    async function loadDeliveryLogs() {
      try {
        const params: Record<string, string | number> = {
          page: currentPage,
          size: pageSize,
        };

        if (statusFilter !== 'ALL') {
          params.status = statusFilter;
        }

        const response = await api.get<WebhookLogsPageResponse>('/api/v1/webhooks/logs', { params });
        if (!isCancelled) {
          const data = response.data;
          setLogs(data.content || []);
          setCurrentPage(data.page);
          setTotalPages(data.totalPages);
          setTotalElements(data.totalElements);
          setLogsError(null);
          setLoadingLogs(false);
        }
      } catch (err: unknown) {
        if (!isCancelled) {
          const error = err as { response?: { status?: number; data?: { message?: string } } };
          const status = error.response?.status;
          const msg = error.response?.data?.message;

          if (status === 503) {
            setLogsError('Merchant identity is currently unavailable. Ensure your account is approved and assigned a Merchant ID.');
          } else if (status === 403) {
            setLogsError('Access restricted. Only verified merchant accounts can view notification history.');
          } else {
            setLogsError(msg || 'Unable to retrieve notification history. Please try again.');
          }
          setLoadingLogs(false);
        }
      }
    }

    loadDeliveryLogs();

    return () => {
      isCancelled = true;
    };
  }, [currentPage, pageSize, statusFilter, refreshTrigger]);

  // Fetch Demo Events
  useEffect(() => {
    let isCancelled = false;

    async function loadDemoEvents() {
      try {
        const response = await api.get<DemoWebhookEventsResponse>('/api/v1/webhooks/demo-events');
        if (!isCancelled) {
          setDemoEvents(response.data.events || []);
          setDemoError(null);
        }
      } catch (err: unknown) {
        if (!isCancelled) {
          const error = err as { response?: { status?: number; data?: { message?: string } } };
          const status = error.response?.status;
          const msg = error.response?.data?.message;

          if (status === 503) {
            setDemoError('Notification activity requires a verified merchant account.');
          } else if (status === 403) {
            setDemoError('Access restricted to verified merchant accounts.');
          } else {
            setDemoError(msg || 'Failed to retrieve notification activity.');
          }
        }
      }
    }

    loadDemoEvents();

    if (!pollingActive) {
      return () => {
        isCancelled = true;
      };
    }

    const interval = setInterval(() => {
      loadDemoEvents();
    }, 3500);

    return () => {
      isCancelled = true;
      clearInterval(interval);
    };
  }, [pollingActive, refreshTrigger]);

  // Handlers for URL Configuration
  const handleSaveUrl = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = urlInput.trim();
    setSavingUrl(true);
    try {
      const response = await api.put<MerchantProfileResponse>('/api/v1/auth/merchant/webhook', {
        webhookUrl: trimmed.length > 0 ? trimmed : null,
        regenerateSecret: false,
      });
      setProfile(response.data);
      setUrlInput(response.data.webhookUrl || '');
      toast.success(
        trimmed.length > 0
          ? 'Delivery notification URL saved successfully.'
          : 'Delivery notification destination cleared.'
      );
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      toast.error(error.response?.data?.message || 'Failed to update destination URL.');
    } finally {
      setSavingUrl(false);
    }
  };

  // Handlers for Security Key
  const handleExecuteKeyGeneration = async () => {
    setConfirmRegenerateOpen(false);
    setRegeneratingSecret(true);
    try {
      const response = await api.put<MerchantProfileResponse>('/api/v1/auth/merchant/webhook', {
        webhookUrl: profile?.webhookUrl || null,
        regenerateSecret: true,
      });
      setProfile(response.data);
      if (response.data.newWebhookSecret) {
        setNewlyGeneratedSecret(response.data.newWebhookSecret);
        toast.success('New security key generated! Please copy and store it securely.');
      } else {
        toast.success('Security key updated successfully.');
      }
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      toast.error(error.response?.data?.message || 'Failed to generate security key.');
    } finally {
      setRegeneratingSecret(false);
    }
  };

  const handleCopyNewSecret = () => {
    if (!newlyGeneratedSecret) return;
    navigator.clipboard.writeText(newlyGeneratedSecret);
    setCopiedNewSecret(true);
    toast.info('Security key copied to clipboard.');
    setTimeout(() => {
      setCopiedNewSecret(false);
    }, 2500);
  };

  // Handlers for Retry, Clear & Copy
  const handleManualRetry = async (logId: string) => {
    setRetryingLogId(logId);
    try {
      const response = await api.post<ManualRetryResponse>(`/api/v1/webhooks/logs/${logId}/retry`);
      toast.success(response.data.message || 'Notification retry scheduled successfully.');
      setRefreshTrigger((prev) => prev + 1);
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      const msg = error.response?.data?.message || 'Failed to schedule notification retry.';
      toast.error(msg);
    } finally {
      setRetryingLogId(null);
    }
  };

  const handleClearDemoEvents = async () => {
    setClearingEvents(true);
    try {
      await api.delete('/api/v1/webhooks/demo-events');
      setDemoEvents([]);
      toast.success('Test notification events cleared.');
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      toast.error(error.response?.data?.message || 'Failed to clear test events.');
    } finally {
      setClearingEvents(false);
    }
  };

  const handleCopyPayload = useCallback((id: string, payloadText: string) => {
    navigator.clipboard.writeText(payloadText);
    setCopiedPayloadId(id);
    toast.info('Notification payload copied to clipboard.');
    setTimeout(() => {
      setCopiedPayloadId(null);
    }, 2000);
  }, [toast]);

  const getStatusBadge = (status: WebhookDeliveryStatus) => {
    switch (status) {
      case 'DELIVERED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
            DELIVERED
          </span>
        );
      case 'RETRYING':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-50 text-amber-700 border border-amber-200">
            <Clock className="w-3.5 h-3.5 text-amber-600 animate-spin" />
            RETRYING
          </span>
        );
      case 'DEAD_LETTER':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-rose-50 text-rose-700 border border-rose-200">
            <XCircle className="w-3.5 h-3.5 text-rose-600" />
            DEAD_LETTER
          </span>
        );
      case 'PENDING':
      default:
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-slate-100 text-slate-700 border border-slate-200">
            <Clock className="w-3.5 h-3.5 text-slate-500" />
            PENDING
          </span>
        );
    }
  };

  const isHttps = urlInput.trim().toLowerCase().startsWith('https://');
  const isHttp = urlInput.trim().toLowerCase().startsWith('http://');
  const hasNonHttpsWarning = isHttp && !isHttps;

  return (
    <AppShell>
      <div className="space-y-6">
        {/* Page Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 bg-white p-6 rounded-2xl border border-slate-200/80 shadow-xs">
          <div>
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center border border-indigo-100 shadow-2xs">
                <Webhook className="w-5 h-5" />
              </div>
              <div>
                <h1 className="text-xl font-bold text-slate-900 tracking-tight">Delivery Notification Center</h1>
                <p className="text-sm text-slate-500">Monitor automatic delivery updates sent securely to your connected business system.</p>
              </div>
            </div>
          </div>
          <div className="flex items-center gap-2.5">
            <button
              onClick={() => {
                setLoadingLogs(true);
                setRefreshTrigger((prev) => prev + 1);
                toast.info('Refreshed notification settings, history, and activity.');
              }}
              className="inline-flex items-center gap-2 px-3.5 py-2 rounded-xl text-sm font-medium bg-slate-100 hover:bg-slate-200 text-slate-700 transition-colors shadow-2xs cursor-pointer"
            >
              <RefreshCw className="w-4 h-4 text-slate-600" />
              Refresh
            </button>
          </div>
        </div>

        {/* Newly Generated Security Key One-Time Display Banner */}
        {newlyGeneratedSecret && (
          <div className="bg-gradient-to-r from-amber-50 to-orange-50 border-2 border-amber-300 rounded-2xl p-6 shadow-sm">
            <div className="flex items-start gap-4">
              <div className="p-2.5 bg-amber-100 text-amber-800 rounded-xl shrink-0">
                <AlertTriangle className="w-6 h-6 text-amber-700" />
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between gap-2 flex-wrap">
                  <h3 className="text-base font-bold text-amber-900">
                    New Security Key Generated (One-Time Display)
                  </h3>
                  <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-200 text-amber-900 border border-amber-300">
                    Save Immediately
                  </span>
                </div>
                <p className="text-sm text-amber-800 mt-1 leading-relaxed">
                  Please copy and store this security key in your business system now. For your security, this complete key will never be displayed again after you leave or refresh this page.
                </p>

                <div className="mt-4 flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
                  <div className="flex-1 min-w-0 bg-white border border-amber-300 rounded-xl px-4 py-2.5 font-mono text-sm text-slate-900 select-all shadow-inner overflow-x-auto">
                    {newlyGeneratedSecret}
                  </div>
                  <button
                    type="button"
                    onClick={handleCopyNewSecret}
                    className="inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl text-sm font-semibold text-white bg-amber-600 hover:bg-amber-700 transition-colors shadow-xs cursor-pointer shrink-0"
                  >
                    {copiedNewSecret ? (
                      <>
                        <Check className="w-4 h-4 text-white" />
                        <span>Copied!</span>
                      </>
                    ) : (
                      <>
                        <Copy className="w-4 h-4 text-white" />
                        <span>Copy Key</span>
                      </>
                    )}
                  </button>
                  <button
                    type="button"
                    onClick={() => setNewlyGeneratedSecret(null)}
                    className="inline-flex items-center justify-center px-4 py-2.5 rounded-xl text-sm font-medium text-slate-700 bg-white hover:bg-slate-100 border border-slate-300 transition-colors cursor-pointer shrink-0"
                  >
                    Done &amp; Saved
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Notification Settings Section */}
        <div className="bg-white border border-slate-200/80 rounded-2xl p-6 shadow-xs space-y-6">
          <div className="flex items-start justify-between gap-4 border-b border-slate-100 pb-5">
            <div className="flex items-start gap-3">
              <div className="p-2.5 bg-indigo-50 text-indigo-600 rounded-xl border border-indigo-100 shrink-0">
                <Shield className="w-5 h-5" />
              </div>
              <div>
                <h2 className="text-base font-bold text-slate-900 tracking-tight">Notification Settings</h2>
                <p className="text-xs text-slate-500 mt-0.5">
                  Manage where your delivery updates are sent and how they are secured.
                </p>
              </div>
            </div>
            {profile?.businessName && (
              <span className="hidden sm:inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold bg-slate-100 text-slate-700 border border-slate-200">
                {profile.businessName}
              </span>
            )}
          </div>

          {profileError && (
            <div className="p-4 bg-rose-50 border border-rose-200 rounded-xl text-rose-800 text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 flex-shrink-0 text-rose-600" />
              <span>{profileError}</span>
            </div>
          )}

          {loadingProfile ? (
            <div className="py-8 text-center text-slate-500">
              <div className="flex items-center justify-center gap-2 text-xs">
                <RefreshCw className="w-4 h-4 animate-spin text-indigo-600" />
                <span>Loading notification settings...</span>
              </div>
            </div>
          ) : (
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              {/* Group 1: Delivery Notification URL */}
              <div className="bg-slate-50/80 border border-slate-200/80 rounded-xl p-5 flex flex-col justify-between">
                <div>
                  <div className="flex items-center gap-2">
                    <Globe className="w-4 h-4 text-indigo-600" />
                    <h3 className="text-sm font-bold text-slate-900">Where should we send your delivery updates?</h3>
                  </div>
                  <p className="text-xs text-slate-500 mt-1 leading-relaxed">
                    Enter the secure URL of your business system to receive automatic parcel status updates.
                  </p>

                  <form onSubmit={handleSaveUrl} className="mt-4 space-y-3">
                    <div>
                      <label htmlFor="destination-url-input" className="block text-[11px] font-semibold text-slate-700 mb-1">
                        Destination URL
                      </label>
                      <input
                        id="destination-url-input"
                        type="text"
                        value={urlInput}
                        onChange={(e) => setUrlInput(e.target.value)}
                        placeholder="https://your-business.com/api/delivery-updates"
                        className="w-full px-3.5 py-2 text-xs bg-white border border-slate-200 rounded-xl focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 text-slate-800 placeholder:text-slate-400 font-mono transition-all"
                      />
                    </div>

                    {hasNonHttpsWarning && (
                      <div className="p-2.5 bg-amber-50 border border-amber-200 rounded-lg text-[11px] text-amber-800 flex items-start gap-2">
                        <Info className="w-3.5 h-3.5 text-amber-600 shrink-0 mt-0.5" />
                        <span>HTTPS is strongly recommended to protect delivery updates and customer data in transit.</span>
                      </div>
                    )}

                    <div className="text-[11px] text-slate-500 space-y-1">
                      <p>
                        Current Destination:{' '}
                        {profile?.webhookUrl ? (
                          <span className="font-mono text-slate-800 font-medium break-all">{profile.webhookUrl}</span>
                        ) : (
                          <span className="italic text-slate-400">Not configured</span>
                        )}
                      </p>
                      <p className="text-slate-400">
                        HTTPS endpoints ensure encrypted in-transit delivery of parcel tracking numbers and address milestones.
                      </p>
                    </div>

                    <div className="pt-2">
                      <button
                        type="submit"
                        disabled={savingUrl}
                        className="inline-flex items-center justify-center gap-2 w-full px-4 py-2 rounded-xl text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 transition-colors shadow-2xs cursor-pointer"
                      >
                        {savingUrl ? (
                          <>
                            <RefreshCw className="w-3.5 h-3.5 animate-spin text-white" />
                            <span>Saving Changes...</span>
                          </>
                        ) : (
                          <>
                            <Check className="w-3.5 h-3.5" />
                            <span>Save Changes</span>
                          </>
                        )}
                      </button>
                    </div>
                  </form>
                </div>
              </div>

              {/* Group 2: Notification Security Key */}
              <div className="bg-slate-50/80 border border-slate-200/80 rounded-xl p-5 flex flex-col justify-between">
                <div>
                  <div className="flex items-center gap-2">
                    <Key className="w-4 h-4 text-indigo-600" />
                    <h3 className="text-sm font-bold text-slate-900">Notification Security Key</h3>
                  </div>
                  <p className="text-xs text-slate-500 mt-1 leading-relaxed">
                    This key helps your business system verify that delivery updates genuinely come from SwiftDrop.
                  </p>

                  <div className="mt-4 space-y-3">
                    <div>
                      <span className="block text-[11px] font-semibold text-slate-700 mb-1">
                        Active Key Status
                      </span>
                      {profile?.webhookSecret ? (
                        <div className="flex items-center justify-between p-2.5 bg-white border border-slate-200 rounded-xl">
                          <div className="flex items-center gap-2 min-w-0">
                            <Lock className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                            <span className="font-mono text-xs text-slate-800 truncate">
                              {profile.webhookSecret}
                            </span>
                          </div>
                          <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200 shrink-0">
                            Active
                          </span>
                        </div>
                      ) : (
                        <div className="p-3 bg-white border border-dashed border-slate-300 rounded-xl text-center">
                          <span className="text-xs text-slate-400 italic">Not configured</span>
                        </div>
                      )}
                    </div>

                    <p className="text-[11px] text-slate-500 leading-relaxed">
                      Store this key securely in your environment variables. It is used to generate an HMAC verification signature on each delivery payload.
                    </p>
                  </div>
                </div>

                <div className="pt-4 mt-2 border-t border-slate-200/60">
                  <button
                    type="button"
                    onClick={() => {
                      if (profile?.webhookSecret) {
                        setConfirmRegenerateOpen(true);
                      } else {
                        handleExecuteKeyGeneration();
                      }
                    }}
                    disabled={regeneratingSecret}
                    className="inline-flex items-center justify-center gap-2 w-full px-4 py-2 rounded-xl text-xs font-semibold text-slate-700 bg-white hover:bg-slate-100 border border-slate-200 disabled:opacity-50 transition-colors shadow-2xs cursor-pointer"
                  >
                    {regeneratingSecret ? (
                      <>
                        <RefreshCw className="w-3.5 h-3.5 animate-spin text-slate-500" />
                        <span>Generating...</span>
                      </>
                    ) : profile?.webhookSecret ? (
                      <>
                        <RotateCcw className="w-3.5 h-3.5 text-slate-600" />
                        <span>Regenerate Key</span>
                      </>
                    ) : (
                      <>
                        <Key className="w-3.5 h-3.5 text-indigo-600" />
                        <span>Generate Key</span>
                      </>
                    )}
                  </button>
                </div>
              </div>

              {/* Group 3: Test Your Connection */}
              <div className="bg-slate-50/80 border border-slate-200/80 rounded-xl p-5 flex flex-col justify-between">
                <div>
                  <div className="flex items-center gap-2">
                    <Send className="w-4 h-4 text-indigo-600" />
                    <h3 className="text-sm font-bold text-slate-900">Test Your Connection</h3>
                  </div>
                  <p className="text-xs text-slate-500 mt-1 leading-relaxed">
                    Check whether your business system can receive delivery updates from SwiftDrop.
                  </p>

                  <div className="mt-4 space-y-3">
                    {!profile?.webhookUrl ? (
                      <div className="p-3 bg-amber-50/80 border border-amber-200/80 rounded-xl text-[11px] text-amber-900 leading-relaxed">
                        <div className="font-semibold text-amber-800 flex items-center gap-1.5 mb-1">
                          <AlertCircle className="w-3.5 h-3.5 text-amber-600" />
                          <span>Destination Not Set</span>
                        </div>
                        Please configure and save your Delivery Notification URL first before testing connectivity.
                      </div>
                    ) : (
                      <div className="p-3 bg-indigo-50/60 border border-indigo-100 rounded-xl text-[11px] text-slate-600 leading-relaxed space-y-1">
                        <div className="font-semibold text-slate-800 flex items-center gap-1.5">
                          <Info className="w-3.5 h-3.5 text-indigo-600 shrink-0" />
                          <span>Automated Delivery Dispatch</span>
                        </div>
                        <p>
                          Whenever a parcel status transitions (e.g. dispatched, delivered), updates are automatically delivered to your saved destination.
                        </p>
                        <p className="text-slate-500 text-[10px]">
                          Automated test dispatches to custom external URLs require the Outbound Test Webhook API (scheduled for backend implementation). In the meantime, outbound parcel status updates will automatically attempt delivery to your saved URL whenever parcel milestones change.
                        </p>
                      </div>
                    )}

                    <div className="text-[11px] text-slate-500 space-y-1.5">
                      <div className="flex items-center justify-between">
                        <span>Configured Endpoint:</span>
                        <span className="font-medium text-slate-800">
                          {profile?.webhookUrl ? 'Configured' : 'None'}
                        </span>
                      </div>
                      <div className="flex items-center justify-between">
                        <span>Sandbox Testing:</span>
                        <span className="font-medium text-indigo-700">Supported Below</span>
                      </div>
                    </div>
                  </div>
                </div>

                <div className="pt-4 mt-2 border-t border-slate-200/60">
                  <button
                    type="button"
                    disabled={true}
                    title="Direct test ping to custom destination URLs will be available in an upcoming backend release."
                    className="inline-flex items-center justify-center gap-2 w-full px-4 py-2 rounded-xl text-xs font-semibold text-slate-400 bg-slate-100 border border-slate-200 cursor-not-allowed opacity-75"
                  >
                    <Send className="w-3.5 h-3.5" />
                    <span>Send Test Notification (Pending API)</span>
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Confirmation Modal for Regenerating Security Key */}
        {confirmRegenerateOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs p-4">
            <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl border border-slate-200 space-y-4 animate-in fade-in zoom-in-95 duration-150">
              <div className="flex items-start gap-3">
                <div className="p-2.5 bg-amber-100 text-amber-700 rounded-xl shrink-0">
                  <AlertTriangle className="w-5 h-5 text-amber-700" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-slate-900">Regenerate Security Key?</h3>
                  <p className="text-xs text-slate-500 mt-1 leading-relaxed">
                    Regenerating your security key will immediately invalidate the previous key. Any delivery updates sent while your business system is updating will fail signature verification until you update the key in your system.
                  </p>
                </div>
              </div>

              <div className="p-3 bg-amber-50 border border-amber-200 rounded-xl text-[11px] text-amber-800">
                You will be shown the new plaintext key exactly once after generation. Make sure you are prepared to store it immediately.
              </div>

              <div className="flex items-center justify-end gap-2.5 pt-2">
                <button
                  type="button"
                  onClick={() => setConfirmRegenerateOpen(false)}
                  disabled={regeneratingSecret}
                  className="px-4 py-2 rounded-xl text-xs font-medium text-slate-700 bg-slate-100 hover:bg-slate-200 transition-colors cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={handleExecuteKeyGeneration}
                  disabled={regeneratingSecret}
                  className="px-4 py-2 rounded-xl text-xs font-semibold text-white bg-amber-600 hover:bg-amber-700 transition-colors shadow-xs cursor-pointer"
                >
                  {regeneratingSecret ? 'Regenerating...' : 'Yes, Regenerate Key'}
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Developer Settings Section */}
        <div className="bg-white border border-slate-200/80 rounded-2xl p-4 shadow-2xs">
          <button
            type="button"
            onClick={() => setShowDeveloperDetails(!showDeveloperDetails)}
            className="w-full flex items-center justify-between text-left text-xs font-semibold text-slate-700 hover:text-indigo-600 transition-colors cursor-pointer"
          >
            <div className="flex items-center gap-2">
              <Code2 className="w-4 h-4 text-slate-400" />
              <span>Developer Settings (Webhooks &amp; API)</span>
            </div>
            <div className="flex items-center gap-1 text-slate-400 text-[11px] font-normal">
              <span>{showDeveloperDetails ? 'Hide' : 'Show'}</span>
              <ChevronDown className={`w-3.5 h-3.5 transition-transform duration-200 ${showDeveloperDetails ? 'rotate-180' : ''}`} />
            </div>
          </button>

          {showDeveloperDetails && (
            <div className="mt-3 pt-3 border-t border-slate-100 text-xs space-y-3">
              <p className="text-slate-500 text-[11px]">
                Technical webhook and API specifications for integrating and testing your system with SwiftDrop.
              </p>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3 font-mono text-[11px]">
                <div className="p-3 bg-slate-50 rounded-xl border border-slate-200/60">
                  <div className="text-slate-500 font-sans text-[10px] font-semibold uppercase tracking-wider mb-1">
                    Test Receiver Endpoint
                  </div>
                  <div className="text-indigo-700 font-medium">POST /api/v1/webhooks/demo-receiver</div>
                  <div className="text-slate-400 font-sans text-[10px] mt-1">Accepts test payloads with signature verification</div>
                </div>
                <div className="p-3 bg-slate-50 rounded-xl border border-slate-200/60">
                  <div className="text-slate-500 font-sans text-[10px] font-semibold uppercase tracking-wider mb-1">
                    Signature Verification Header
                  </div>
                  <div className="text-slate-700 font-medium">X-SwiftDrop-Signature (HMAC-SHA256)</div>
                  <div className="text-slate-400 font-sans text-[10px] mt-1">Hex-encoded digest computed over raw request body</div>
                </div>
                <div className="p-3 bg-slate-50 rounded-xl border border-slate-200/60">
                  <div className="text-slate-500 font-sans text-[10px] font-semibold uppercase tracking-wider mb-1">
                    Live Event Activity API
                  </div>
                  <div className="text-slate-700 font-medium">GET /api/v1/webhooks/demo-events</div>
                  <div className="text-slate-400 font-sans text-[10px] mt-1">Polls verified webhook payloads captured for merchant</div>
                </div>
                <div className="p-3 bg-slate-50 rounded-xl border border-slate-200/60">
                  <div className="text-slate-500 font-sans text-[10px] font-semibold uppercase tracking-wider mb-1">
                    Notification History API
                  </div>
                  <div className="text-slate-700 font-medium">GET /api/v1/webhooks/logs</div>
                  <div className="text-slate-400 font-sans text-[10px] mt-1">Paginated delivery audit trail with status filtering</div>
                </div>
              </div>
              <div className="p-3 bg-indigo-50/50 rounded-xl border border-indigo-100 text-[11px] text-slate-600 font-sans space-y-1">
                <div className="font-semibold text-slate-800 text-xs">Webhook Delivery &amp; Retry Behavior:</div>
                <p>
                  Outbound webhooks follow a 5-tier exponential backoff schedule (10s, 30s, 2m, 10m, 30m). Deliveries that exhaust all attempts transition to <span className="font-mono font-semibold text-rose-700">DEAD_LETTER</span> status and become eligible for manual retry via <span className="font-mono text-indigo-700">POST /api/v1/webhooks/logs/&#123;id&#125;/retry</span>.
                </p>
              </div>
            </div>
          )}
        </div>

        {/* Notification Activity Section (Demo Stream) */}
        <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden">
          <div className="px-6 py-4 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-gradient-to-r from-slate-50/50 to-white">
            <div className="flex items-center gap-2.5">
              <div className="relative flex items-center justify-center">
                <span className={`w-2.5 h-2.5 rounded-full ${pollingActive ? 'bg-emerald-500' : 'bg-slate-400'}`}></span>
                {pollingActive && <span className="absolute w-2.5 h-2.5 rounded-full bg-emerald-400 animate-ping opacity-75"></span>}
              </div>
              <div>
                <h2 className="text-sm font-bold text-slate-900 tracking-tight flex items-center gap-2">
                  Notification Activity
                </h2>
                <p className="text-xs text-slate-500">View delivery notifications received by your connected system and monitor their activity.</p>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setPollingActive(!pollingActive)}
                className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-medium border transition-colors cursor-pointer ${
                  pollingActive
                    ? 'bg-emerald-50 text-emerald-700 border-emerald-200 hover:bg-emerald-100'
                    : 'bg-slate-100 text-slate-700 border-slate-200 hover:bg-slate-200'
                }`}
              >
                <Radio className="w-3.5 h-3.5" />
                {pollingActive ? 'Live Updates Active' : 'Live Updates Paused'}
              </button>
              <button
                onClick={handleClearDemoEvents}
                disabled={clearingEvents || demoEvents.length === 0}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-medium text-rose-600 bg-rose-50 hover:bg-rose-100 border border-rose-200 disabled:opacity-50 disabled:cursor-not-allowed transition-colors cursor-pointer"
              >
                <Trash2 className="w-3.5 h-3.5" />
                Clear Test Events
              </button>
            </div>
          </div>

          {demoError && (
            <div className="p-4 m-4 bg-amber-50 border border-amber-200 rounded-xl text-amber-800 text-xs flex items-center gap-2">
              <Info className="w-4 h-4 flex-shrink-0 text-amber-600" />
              <span>{demoError}</span>
            </div>
          )}

          <div className="p-6">
            {demoEvents.length === 0 ? (
              <div className="text-center py-8 text-slate-500">
                <Webhook className="w-8 h-8 text-slate-300 mx-auto mb-2" />
                <p className="text-sm font-medium text-slate-700">No notifications received yet.</p>
                <p className="text-xs text-slate-400 mt-1 max-w-md mx-auto">
                  Delivery updates will appear here when notifications are received by the test system.
                </p>
              </div>
            ) : (
              <div className="space-y-3">
                {demoEvents.map((evt) => (
                  <div key={evt.id} className="border border-slate-200/80 rounded-xl p-4 bg-slate-50/50 hover:bg-slate-50 transition-colors">
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                      <div className="flex items-center gap-2.5">
                        <span className="px-2 py-0.5 rounded-md text-xs font-mono font-medium bg-indigo-100 text-indigo-800 border border-indigo-200">
                          {evt.eventType}
                        </span>
                        <span className="inline-flex items-center gap-1 text-xs font-medium text-emerald-700 bg-emerald-100/70 px-2 py-0.5 rounded-full">
                          <Check className="w-3 h-3 text-emerald-600" />
                          {evt.verificationStatus}
                        </span>
                      </div>
                      <div className="flex items-center gap-3 text-xs text-slate-500">
                        <span>{new Date(evt.receivedAt).toLocaleTimeString()}</span>
                        <button
                          onClick={() => setExpandedPayloadId(expandedPayloadId === evt.id ? null : evt.id)}
                          className="text-indigo-600 hover:text-indigo-700 font-semibold cursor-pointer"
                        >
                          {expandedPayloadId === evt.id ? 'Hide Details' : 'View Details'}
                        </button>
                      </div>
                    </div>

                    {expandedPayloadId === evt.id && (
                      <div className="mt-3 relative rounded-xl bg-slate-900 p-4 text-xs font-mono text-slate-100 shadow-inner overflow-x-auto">
                        <button
                          onClick={() => handleCopyPayload(evt.id, evt.payload)}
                          className="absolute top-3 right-3 p-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white transition-colors cursor-pointer"
                          title="Copy payload"
                        >
                          {copiedPayloadId === evt.id ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                        </button>
                        <pre className="text-xs leading-relaxed text-indigo-300">
                          {(() => {
                            try {
                              return JSON.stringify(JSON.parse(evt.payload), null, 2);
                            } catch {
                              return evt.payload;
                            }
                          })()}
                        </pre>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Notification History Section (Delivery Logs) */}
        <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden">
          <div className="p-6 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <h2 className="text-base font-bold text-slate-900 tracking-tight flex items-center gap-2">
                Notification History
                <span className="text-xs font-semibold px-2 py-0.5 bg-slate-100 text-slate-600 rounded-full">
                  {totalElements} Total
                </span>
              </h2>
              <p className="text-xs text-slate-500">Audit trail of automated delivery updates with status tracking and retry capability.</p>
            </div>

            <div className="flex flex-wrap items-center gap-1.5 bg-slate-100/80 p-1 rounded-xl">
              {['ALL', 'DELIVERED', 'RETRYING', 'DEAD_LETTER', 'PENDING'].map((status) => (
                <button
                  key={status}
                  onClick={() => {
                    setStatusFilter(status);
                    setCurrentPage(0);
                    setLoadingLogs(true);
                  }}
                  className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-all cursor-pointer ${
                    statusFilter === status
                      ? 'bg-white text-slate-900 shadow-2xs font-semibold'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  {status}
                </button>
              ))}
            </div>
          </div>

          {logsError && (
            <div className="p-4 m-6 bg-rose-50 border border-rose-200 rounded-xl text-rose-800 text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 flex-shrink-0 text-rose-600" />
              <span>{logsError}</span>
            </div>
          )}

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-slate-100 bg-slate-50/70 text-slate-500 font-semibold uppercase tracking-wider">
                  <th className="py-3 px-6">Event Type</th>
                  <th className="py-3 px-6">Delivery ID &amp; Parcel</th>
                  <th className="py-3 px-6">Status</th>
                  <th className="py-3 px-6">HTTP Code</th>
                  <th className="py-3 px-6">Retry Count</th>
                  <th className="py-3 px-6">Created / Next Retry</th>
                  <th className="py-3 px-6 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {loadingLogs ? (
                  <tr>
                    <td colSpan={7} className="py-12 text-center text-slate-500">
                      <div className="flex items-center justify-center gap-2">
                        <RefreshCw className="w-4 h-4 animate-spin text-indigo-600" />
                        <span>Loading notification history...</span>
                      </div>
                    </td>
                  </tr>
                ) : logs.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="py-12 text-center text-slate-500">
                      <div className="max-w-sm mx-auto">
                        <SlidersHorizontal className="w-8 h-8 text-slate-300 mx-auto mb-2" />
                        <p className="text-sm font-medium text-slate-700">No notification history found</p>
                        <p className="text-xs text-slate-400 mt-1">
                          No notifications match status <span className="font-semibold text-slate-600">&quot;{statusFilter}&quot;</span>.
                        </p>
                      </div>
                    </td>
                  </tr>
                ) : (
                  logs.map((logItem) => (
                    <tr key={logItem.id} className="hover:bg-slate-50/80 transition-colors">
                      <td className="py-3.5 px-6 font-mono font-medium text-slate-800">
                        {logItem.eventType}
                      </td>
                      <td className="py-3.5 px-6">
                        <div className="font-mono text-slate-700 text-xs">{logItem.id.substring(0, 8)}...</div>
                        <div className="text-[11px] text-slate-400 font-mono">Parcel: {logItem.parcelId.substring(0, 8)}...</div>
                      </td>
                      <td className="py-3.5 px-6">
                        {getStatusBadge(logItem.deliveryStatus)}
                      </td>
                      <td className="py-3.5 px-6">
                        {logItem.httpStatusCode ? (
                          <span className={`font-mono font-semibold ${logItem.httpStatusCode >= 200 && logItem.httpStatusCode < 300 ? 'text-emerald-700' : 'text-rose-600'}`}>
                            {logItem.httpStatusCode}
                          </span>
                        ) : (
                          <span className="text-slate-400 italic">—</span>
                        )}
                      </td>
                      <td className="py-3.5 px-6 font-mono text-slate-700">
                        {logItem.retryCount} / 5
                      </td>
                      <td className="py-3.5 px-6 text-slate-500">
                        <div>{new Date(logItem.createdAt).toLocaleString()}</div>
                        {logItem.nextRetryAt && (
                          <div className="text-[11px] text-amber-600 flex items-center gap-1 mt-0.5">
                            <Clock className="w-3 h-3" />
                            Next: {new Date(logItem.nextRetryAt).toLocaleTimeString()}
                          </div>
                        )}
                      </td>
                      <td className="py-3.5 px-6 text-right">
                        {logItem.deliveryStatus === 'DEAD_LETTER' ? (
                          <button
                            onClick={() => handleManualRetry(logItem.id)}
                            disabled={retryingLogId === logItem.id}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold text-indigo-700 bg-indigo-50 hover:bg-indigo-100 border border-indigo-200 transition-colors disabled:opacity-50 cursor-pointer"
                          >
                            <RotateCcw className={`w-3.5 h-3.5 ${retryingLogId === logItem.id ? 'animate-spin' : ''}`} />
                            {retryingLogId === logItem.id ? 'Retrying...' : 'Retry Notification'}
                          </button>
                        ) : (
                          <span className="text-slate-400 text-xs">—</span>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          <div className="p-4 border-t border-slate-100 bg-slate-50/50 flex items-center justify-between text-xs text-slate-600">
            <div>
              Showing page <span className="font-semibold text-slate-900">{currentPage + 1}</span> of{' '}
              <span className="font-semibold text-slate-900">{Math.max(1, totalPages)}</span> ({totalElements} total entries)
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => {
                  setLoadingLogs(true);
                  setCurrentPage((prev) => Math.max(0, prev - 1));
                }}
                disabled={currentPage <= 0 || loadingLogs}
                className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg border border-slate-200 bg-white hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors cursor-pointer"
              >
                <ChevronLeft className="w-4 h-4" />
                Previous
              </button>
              <button
                onClick={() => {
                  setLoadingLogs(true);
                  setCurrentPage((prev) => prev + 1);
                }}
                disabled={currentPage >= totalPages - 1 || loadingLogs}
                className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg border border-slate-200 bg-white hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors cursor-pointer"
              >
                Next
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </AppShell>
  );
}
