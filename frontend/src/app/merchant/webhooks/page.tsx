'use client';

import React, { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import {
  Webhook,
  Save,
  RefreshCw,
  Copy,
  Check,
  CheckCircle2,
  ShieldAlert,
  ShieldCheck,
  Trash2,
  ChevronDown,
  KeyRound,
} from 'lucide-react';
import AppShell from '@/components/layout/AppShell';
import { api } from '@/lib/api';
import { apiErrorMessage, isValidWebhookUrl } from '@/lib/merchant/format';
import type { MerchantProfile, WebhookEvent, WebhookEventsResponse } from '@/types/merchant';

/** SwiftDrop's built-in receiver, for trying webhooks before a store endpoint is ready. */
const DEMO_RECEIVER_URL = 'http://localhost:8083/api/v1/webhooks/demo-receiver';

/** The notification service fails closed (503) until it can map a user to a merchant on the server. */
function eventsErrorMessage(err: unknown): string {
  const res = (err as { response?: { status?: number; data?: { error?: string } } })?.response;
  if (res?.status === 503 || res?.data?.error === 'AUTH_MAPPING_UNAVAILABLE') {
    return 'The event log is not available yet, because the notification service cannot confirm your merchant account. Your webhook URL and signing secret above still work.';
  }
  return apiErrorMessage(err, 'Could not load received webhook events.');
}

function prettyPayload(payload: string): string {
  try {
    return JSON.stringify(JSON.parse(payload), null, 2);
  } catch {
    return payload;
  }
}

export default function MerchantWebhooksPage() {
  const [profile, setProfile] = useState<MerchantProfile | null>(null);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [urlInput, setUrlInput] = useState('');
  const [saving, setSaving] = useState(false);
  const [saveMessage, setSaveMessage] = useState<{ kind: 'ok' | 'error'; text: string } | null>(null);

  const [regenerating, setRegenerating] = useState(false);
  const [confirmRegenerate, setConfirmRegenerate] = useState(false);
  const [newSecret, setNewSecret] = useState<string | null>(null);
  const [copied, setCopied] = useState<string | null>(null);

  const [events, setEvents] = useState<WebhookEvent[]>([]);
  const [eventsLoading, setEventsLoading] = useState(true);
  const [eventsError, setEventsError] = useState<string | null>(null);
  const [eventsReloadKey, setEventsReloadKey] = useState(0);
  const [openEventId, setOpenEventId] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    api
      .get<MerchantProfile>('/api/v1/auth/merchant/profile')
      .then((res) => {
        if (cancelled) return;
        setProfile(res.data);
        setUrlInput(res.data.webhookUrl ?? '');
      })
      .catch((err) => {
        if (!cancelled) setProfileError(apiErrorMessage(err, 'Could not load your webhook settings.'));
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    api
      .get<WebhookEventsResponse>('/api/v1/webhooks/demo-events')
      .then((res) => {
        if (cancelled) return;
        setEvents(res.data.events ?? []);
        setEventsError(null);
      })
      .catch((err) => {
        if (!cancelled) setEventsError(eventsErrorMessage(err));
      })
      .finally(() => {
        if (!cancelled) setEventsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [eventsReloadKey]);

  const reloadEvents = useCallback(() => {
    setEventsLoading(true);
    setEventsReloadKey((k) => k + 1);
  }, []);

  const urlValid = isValidWebhookUrl(urlInput);
  const urlChanged = profile !== null && urlInput.trim() !== (profile.webhookUrl ?? '');

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!urlValid || !urlChanged) return;
    setSaving(true);
    setSaveMessage(null);
    try {
      const res = await api.put<MerchantProfile>('/api/v1/auth/merchant/webhook', { webhookUrl: urlInput.trim() });
      setProfile(res.data);
      setUrlInput(res.data.webhookUrl ?? '');
      setSaveMessage({
        kind: 'ok',
        text: res.data.webhookUrl ? 'Webhook URL saved. New parcel updates go to this address.' : 'Webhook URL removed. Parcel updates will not be sent.',
      });
    } catch (err) {
      setSaveMessage({ kind: 'error', text: apiErrorMessage(err, 'Could not save the webhook URL. Your previous URL is unchanged.') });
    } finally {
      setSaving(false);
    }
  };

  const handleRegenerate = async () => {
    setConfirmRegenerate(false);
    setRegenerating(true);
    setSaveMessage(null);
    try {
      const res = await api.put<MerchantProfile>('/api/v1/auth/merchant/webhook', { regenerateSecret: true });
      const { newWebhookSecret, ...updated } = res.data;
      setProfile(updated);
      setNewSecret(newWebhookSecret ?? null);
      if (!newWebhookSecret) {
        setSaveMessage({
          kind: 'error',
          text: 'The secret was regenerated, but the server did not return it. Regenerate again to get a usable secret.',
        });
      }
    } catch (err) {
      setSaveMessage({ kind: 'error', text: apiErrorMessage(err, 'Could not regenerate the secret. Your current secret still works.') });
    } finally {
      setRegenerating(false);
    }
  };

  const handleClearEvents = async () => {
    try {
      await api.delete('/api/v1/webhooks/demo-events');
      setEvents([]);
    } catch (err) {
      setEventsError(eventsErrorMessage(err));
    }
  };

  const copy = async (value: string, key: string) => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(key);
      setTimeout(() => setCopied(null), 2500);
    } catch {
      setCopied(null);
    }
  };

  return (
    <AppShell>
      <div className="space-y-6">
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 bg-white p-6 rounded-2xl border border-slate-200 shadow-xs">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-blue-600 flex items-center justify-center text-white shadow-md shadow-blue-500/20">
              <Webhook className="w-6 h-6" />
            </div>
            <div>
              <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Webhook Management</h1>
              <p className="text-sm text-slate-500">Get parcel status updates sent to your store as they happen</p>
            </div>
          </div>
          <Link href="/merchant/dashboard" className="text-xs font-semibold text-blue-600 hover:text-blue-700 self-start md:self-auto">
            Back to Dashboard &rarr;
          </Link>
        </div>

        {profileError && (
          <div role="alert" className="flex items-start gap-3 p-4 bg-rose-50 border border-rose-200 rounded-2xl text-sm text-rose-800">
            <ShieldAlert className="w-5 h-5 shrink-0 mt-0.5" />
            <p>{profileError}</p>
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Endpoint URL */}
          <form onSubmit={handleSave} className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs space-y-4">
            <div>
              <h2 className="text-base font-bold text-slate-900">Endpoint URL</h2>
              <p className="text-xs text-slate-500 mt-1">
                SwiftDrop sends a signed POST request here whenever one of your parcels changes status. Leave it empty to stop sending updates.
              </p>
            </div>
            <div className="space-y-1.5">
              <label htmlFor="webhook-url" className="text-xs font-bold text-slate-700 uppercase tracking-wider">
                Webhook URL
              </label>
              <input
                id="webhook-url"
                type="url"
                inputMode="url"
                placeholder="https://your-store.lk/webhooks/swiftdrop"
                value={urlInput}
                onChange={(e) => {
                  setUrlInput(e.target.value);
                  setSaveMessage(null);
                }}
                disabled={!profile}
                aria-invalid={!urlValid}
                className={`w-full bg-slate-50 border rounded-xl px-3 py-2 text-xs font-mono text-slate-800 focus:outline-hidden focus:ring-2 focus:ring-blue-500 ${
                  urlValid ? 'border-slate-200' : 'border-rose-400'
                }`}
              />
              {!urlValid && <p className="text-xs text-rose-700">Enter a full address starting with https:// or http://</p>}
              <button
                type="button"
                onClick={() => setUrlInput(DEMO_RECEIVER_URL)}
                disabled={!profile}
                className="text-xs font-semibold text-blue-600 hover:text-blue-800"
              >
                Use the SwiftDrop demo receiver
              </button>
            </div>

            {saveMessage && (
              <div
                role={saveMessage.kind === 'error' ? 'alert' : 'status'}
                className={`p-3 rounded-xl text-xs border ${
                  saveMessage.kind === 'ok' ? 'bg-emerald-50 border-emerald-200 text-emerald-800' : 'bg-rose-50 border-rose-200 text-rose-800'
                }`}
              >
                {saveMessage.text}
              </div>
            )}

            <button
              type="submit"
              disabled={!profile || saving || !urlValid || !urlChanged}
              className="inline-flex items-center gap-2 px-4 py-2.5 text-xs font-bold text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-50 rounded-xl shadow-md shadow-blue-500/20 transition-all"
            >
              {saving ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : <Save className="w-3.5 h-3.5" />}
              {saving ? 'Saving...' : 'Save URL'}
            </button>
          </form>

          {/* Signing secret */}
          <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs space-y-4">
            <div>
              <h2 className="text-base font-bold text-slate-900">Signing secret</h2>
              <p className="text-xs text-slate-500 mt-1">
                Each request carries an <code className="font-mono">X-SwiftDrop-Signature: sha256=&lt;hex&gt;</code> header: the
                HMAC-SHA256 of the raw request body, keyed with this secret. Reject requests whose signature does not match.
              </p>
            </div>
            <div className="flex items-center gap-2 p-3 bg-slate-50 rounded-xl border border-slate-200">
              <KeyRound className="w-4 h-4 text-slate-400" />
              <span className="text-xs font-mono text-slate-600">{profile?.webhookSecret || 'No secret set'}</span>
            </div>

            {newSecret && (
              <div className="p-4 bg-emerald-50 border border-emerald-200 rounded-2xl space-y-2">
                <div className="flex items-center gap-2 text-emerald-800 font-bold text-xs uppercase">
                  <CheckCircle2 className="w-4 h-4" /> New signing secret
                </div>
                <p className="text-xs text-emerald-700">Copy it now and update your store. It will not be shown again.</p>
                <div className="flex items-center gap-2">
                  <input
                    id="new-webhook-secret"
                    type="text"
                    readOnly
                    value={newSecret}
                    className="flex-1 min-w-0 bg-white border border-emerald-300 rounded-xl px-3 py-2 text-xs font-mono text-emerald-900 font-bold"
                  />
                  <button
                    onClick={() => copy(newSecret, 'secret')}
                    className="p-2 bg-emerald-600 text-white hover:bg-emerald-700 rounded-xl transition-all"
                    aria-label="Copy signing secret"
                  >
                    {copied === 'secret' ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                  </button>
                </div>
              </div>
            )}

            {confirmRegenerate ? (
              <div className="p-4 bg-amber-50 border border-amber-200 rounded-2xl space-y-3">
                <p className="text-xs text-amber-900">
                  Requests are signed with the new secret straight away, so your store rejects them until you update it. Continue?
                </p>
                <div className="flex gap-2">
                  <button
                    onClick={handleRegenerate}
                    className="px-4 py-2 text-xs font-bold text-white bg-amber-600 hover:bg-amber-700 rounded-xl"
                  >
                    Regenerate secret
                  </button>
                  <button
                    onClick={() => setConfirmRegenerate(false)}
                    className="px-4 py-2 text-xs font-bold text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-xl"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            ) : (
              <button
                onClick={() => setConfirmRegenerate(true)}
                disabled={!profile || regenerating}
                className="inline-flex items-center gap-2 px-4 py-2.5 text-xs font-bold text-amber-800 bg-amber-50 hover:bg-amber-100 border border-amber-200 disabled:opacity-50 rounded-xl transition-all"
              >
                <RefreshCw className={`w-3.5 h-3.5 ${regenerating ? 'animate-spin' : ''}`} />
                {regenerating ? 'Regenerating...' : 'Regenerate secret'}
              </button>
            )}
          </div>
        </div>

        {/* Received events (demo receiver) */}
        <div className="bg-white rounded-2xl border border-slate-200 shadow-xs overflow-hidden">
          <div className="px-6 py-4 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
            <div>
              <h2 className="text-base font-bold text-slate-900">Events received by the demo receiver</h2>
              <p className="text-xs text-slate-500">
                Shows deliveries to the SwiftDrop demo receiver (the latest 100). Events sent to your own URL appear in your store&apos;s logs.
              </p>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={reloadEvents}
                disabled={eventsLoading}
                className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-100 transition-all"
                aria-label="Refresh events"
                title="Refresh"
              >
                <RefreshCw className={`w-4 h-4 ${eventsLoading ? 'animate-spin text-blue-600' : ''}`} />
              </button>
              <button
                onClick={handleClearEvents}
                disabled={events.length === 0}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-rose-700 bg-rose-50 hover:bg-rose-100 disabled:opacity-40 rounded-lg"
              >
                <Trash2 className="w-3.5 h-3.5" /> Clear
              </button>
            </div>
          </div>

          {eventsError && (
            <div role="alert" className="mx-6 mt-4 p-3 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-800">
              {eventsError}
            </div>
          )}

          {eventsLoading ? (
            <p className="py-10 text-center text-sm text-slate-400">Loading events...</p>
          ) : events.length === 0 ? (
            <p className="py-10 px-6 text-center text-sm text-slate-400">
              No events yet. Set the URL to the demo receiver, then book a parcel to see its updates arrive here.
            </p>
          ) : (
            <ul className="divide-y divide-slate-100">
              {events.map((event) => {
                const open = openEventId === event.id;
                return (
                  <li key={event.id}>
                    <button
                      onClick={() => setOpenEventId(open ? null : event.id)}
                      aria-expanded={open}
                      className="w-full px-6 py-3 flex items-center gap-3 text-left hover:bg-slate-50/60"
                    >
                      {event.verified ? (
                        <ShieldCheck className="w-4 h-4 text-emerald-600 shrink-0" aria-label="Signature verified" />
                      ) : (
                        <ShieldAlert className="w-4 h-4 text-rose-600 shrink-0" aria-label="Signature not verified" />
                      )}
                      <span className="font-mono text-xs font-semibold text-slate-800">{event.eventType}</span>
                      <span className="text-2xs text-slate-400">{event.verificationStatus}</span>
                      <span className="ml-auto text-xs text-slate-500 tabular-nums">
                        {new Date(event.receivedAt).toLocaleString()}
                      </span>
                      <ChevronDown className={`w-4 h-4 text-slate-400 transition-transform ${open ? 'rotate-180' : ''}`} />
                    </button>
                    {open && (
                      <pre className="mx-6 mb-4 p-3 bg-slate-900 text-slate-100 rounded-xl text-2xs overflow-x-auto">
                        {prettyPayload(event.payload)}
                      </pre>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </div>
    </AppShell>
  );
}
