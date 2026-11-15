'use client';

import { FormEvent, useEffect, useState } from 'react';
import Link from 'next/link';
import {
  ArrowLeft,
  Check,
  CheckCircle2,
  Copy,
  ExternalLink,
  RefreshCw,
  Save,
  ShieldCheck,
  Webhook,
} from 'lucide-react';
import AppShell from '@/components/layout/AppShell';
import { api } from '@/lib/api';

interface MerchantProfile {
  merchantId: string;
  businessName: string;
  registrationNumber: string;
  oauthClientId: string;
  webhookUrl: string | null;
  webhookSecret: string;
  newWebhookSecret?: string;
}

function apiErrorMessage(error: unknown, fallback: string) {
  const data = (error as { response?: { data?: { detail?: string; message?: string } } })?.response?.data;
  return data?.detail || data?.message || fallback;
}

export default function MerchantWebhooksPage() {
  const [profile, setProfile] = useState<MerchantProfile | null>(null);
  const [webhookUrl, setWebhookUrl] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [regenerating, setRegenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [newSecret, setNewSecret] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    let cancelled = false;
    api.get<MerchantProfile>('/api/v1/auth/merchant/profile')
      .then((response) => {
        if (cancelled) return;
        setProfile(response.data);
        setWebhookUrl(response.data.webhookUrl ?? '');
      })
      .catch((loadError) => {
        if (!cancelled) setError(apiErrorMessage(loadError, 'Could not load your webhook configuration.'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const saveWebhook = async (event: FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setError(null);
    setSuccess(null);
    setNewSecret(null);
    try {
      const response = await api.put<MerchantProfile>('/api/v1/auth/merchant/webhook', {
        webhookUrl: webhookUrl.trim() || null,
        regenerateSecret: false,
      });
      setProfile(response.data);
      setWebhookUrl(response.data.webhookUrl ?? '');
      setSuccess('Webhook endpoint saved successfully.');
    } catch (saveError) {
      setError(apiErrorMessage(saveError, 'Could not save your webhook endpoint.'));
    } finally {
      setSaving(false);
    }
  };

  const regenerateSecret = async () => {
    if (!confirm('Regenerate the webhook signing secret? Your current secret will stop working immediately.')) {
      return;
    }

    setRegenerating(true);
    setError(null);
    setSuccess(null);
    setNewSecret(null);
    try {
      const response = await api.put<MerchantProfile>('/api/v1/auth/merchant/webhook', {
        webhookUrl: webhookUrl.trim() || null,
        regenerateSecret: true,
      });
      const { newWebhookSecret, ...safeProfile } = response.data;
      setProfile(safeProfile);
      setWebhookUrl(safeProfile.webhookUrl ?? '');
      setNewSecret(newWebhookSecret ?? null);
      if (!newWebhookSecret) {
        setError('The secret was regenerated, but the one-time value was not returned. Please regenerate it again.');
      } else {
        setSuccess('Webhook signing secret regenerated. Copy and store it now.');
      }
    } catch (regenerateError) {
      setError(apiErrorMessage(regenerateError, 'Could not regenerate the webhook signing secret.'));
    } finally {
      setRegenerating(false);
    }
  };

  const copySecret = async () => {
    if (!newSecret) return;
    await navigator.clipboard.writeText(newSecret);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <AppShell>
      <div className="space-y-6">
        <header className="flex flex-col gap-4 border-b border-slate-200 pb-6 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="mb-2 text-xs font-bold uppercase tracking-[0.18em] text-blue-600">Merchant Portal / Integrations</p>
            <h1 className="text-3xl font-black tracking-tight text-slate-950">Webhook Management</h1>
            <p className="mt-2 text-sm text-slate-600">Configure the endpoint that receives signed parcel status updates.</p>
          </div>
          <Link href="/merchant/dashboard" className="inline-flex items-center gap-2 text-sm font-semibold text-slate-600 hover:text-slate-950">
            <ArrowLeft className="h-4 w-4" /> Back to Dashboard
          </Link>
        </header>

        {error && <div role="alert" className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">{error}</div>}
        {success && <div role="status" className="flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700"><CheckCircle2 className="h-4 w-4" />{success}</div>}

        <div className="grid gap-6 lg:grid-cols-[1.4fr_0.8fr]">
          <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            <div className="mb-6 flex items-start gap-3">
              <div className="rounded-xl bg-blue-50 p-3 text-blue-600"><Webhook className="h-5 w-5" /></div>
              <div>
                <h2 className="text-lg font-bold text-slate-950">Delivery endpoint</h2>
                <p className="mt-1 text-sm text-slate-500">SwiftDrop sends parcel lifecycle events to this URL using HTTP POST.</p>
              </div>
            </div>

            {loading ? (
              <div className="flex items-center gap-2 py-8 text-sm text-slate-500"><RefreshCw className="h-4 w-4 animate-spin" /> Loading webhook configuration...</div>
            ) : (
              <form onSubmit={saveWebhook} className="space-y-5">
                <label className="block">
                  <span className="mb-2 block text-xs font-bold uppercase tracking-wider text-slate-600">Webhook URL</span>
                  <input
                    type="url"
                    value={webhookUrl}
                    onChange={(event) => setWebhookUrl(event.target.value)}
                    placeholder="https://merchant.example.com/webhooks/swiftdrop"
                    className="w-full rounded-xl border border-slate-300 px-4 py-3 font-mono text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:ring-4 focus:ring-blue-100"
                  />
                  <span className="mt-2 block text-xs text-slate-500">Leave blank to disable outbound delivery. HTTPS is recommended outside local development.</span>
                </label>

                <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
                  <p className="text-xs font-bold uppercase tracking-wider text-slate-600">Current signing secret</p>
                  <p className="mt-2 font-mono text-sm text-slate-800">{profile?.webhookSecret || 'Not configured'}</p>
                  <p className="mt-2 text-xs text-slate-500">Only the masked value is shown after creation. Verify the <code className="font-mono">X-SwiftDrop-Signature</code> header with HMAC-SHA256.</p>
                </div>

                {newSecret && (
                  <div className="rounded-xl border border-amber-300 bg-amber-50 p-4">
                    <p className="text-sm font-bold text-amber-900">One-time secret - store it now</p>
                    <div className="mt-3 flex gap-2">
                      <input readOnly value={newSecret} aria-label="New webhook signing secret" className="min-w-0 flex-1 rounded-lg border border-amber-300 bg-white px-3 py-2 font-mono text-sm" />
                      <button type="button" onClick={copySecret} className="rounded-lg border border-amber-300 bg-white px-3 text-amber-900 hover:bg-amber-100" aria-label="Copy new webhook signing secret">
                        {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
                      </button>
                    </div>
                  </div>
                )}

                <div className="flex flex-col gap-3 sm:flex-row">
                  <button type="submit" disabled={saving} className="inline-flex items-center justify-center gap-2 rounded-xl bg-slate-950 px-5 py-3 text-sm font-bold text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60">
                    {saving ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}{saving ? 'Saving...' : 'Save Webhook URL'}
                  </button>
                  <button type="button" onClick={regenerateSecret} disabled={regenerating} className="inline-flex items-center justify-center gap-2 rounded-xl border border-slate-300 px-5 py-3 text-sm font-bold text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60">
                    <RefreshCw className={`h-4 w-4 ${regenerating ? 'animate-spin' : ''}`} />{regenerating ? 'Regenerating...' : 'Regenerate Signing Secret'}
                  </button>
                </div>
              </form>
            )}
          </section>

          <aside className="space-y-4">
            <div className="rounded-2xl border border-slate-200 bg-slate-950 p-6 text-white shadow-sm">
              <ShieldCheck className="h-7 w-7 text-emerald-400" />
              <h2 className="mt-4 text-lg font-bold">Secure delivery checklist</h2>
              <ul className="mt-4 space-y-3 text-sm text-slate-300">
                <li>1. Accept only HTTPS requests in production.</li>
                <li>2. Verify the HMAC signature before processing.</li>
                <li>3. Return a 2xx response quickly to acknowledge delivery.</li>
                <li>4. Store event IDs to make processing idempotent.</li>
              </ul>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <p className="text-xs font-bold uppercase tracking-wider text-slate-500">OAuth Client ID</p>
              <p className="mt-2 break-all font-mono text-sm text-slate-900">{profile?.oauthClientId || 'Loading...'}</p>
              <Link href="/merchant/dashboard" className="mt-4 inline-flex items-center gap-1 text-sm font-bold text-blue-600 hover:text-blue-700">Manage API credentials <ExternalLink className="h-3.5 w-3.5" /></Link>
            </div>
          </aside>
        </div>
      </div>
    </AppShell>
  );
}
