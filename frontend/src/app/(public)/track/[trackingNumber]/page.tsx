'use client';

import { Suspense, useEffect, useMemo, useState } from 'react';
import { ArrowLeft, Image as ImageIcon, MapPin, Package, RefreshCw } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import TrackingMilestoneStepper from '@/components/tracking/TrackingMilestoneStepper';
import TrackingTimeline from '@/components/tracking/TrackingTimeline';
import LiveConnectionBadge from '@/components/tracking/LiveConnectionBadge';
import Spinner from '@/components/ui/Spinner';
import { useParcelTracking } from '@/hooks/useParcelTracking';
import { useParcelWebSocket } from '@/lib/websocket';
import type { ParcelTrackingResponse, ParcelTrackingUpdate } from '@/types/tracking';

function TrackingPageContent() {
  const params = useParams<{ trackingNumber: string }>();
  const trackingNumber = decodeURIComponent(params.trackingNumber ?? '');
  const { tracking: initialTracking, loading, error, refetchTracking } = useParcelTracking(trackingNumber);
  const [tracking, setTracking] = useState<ParcelTrackingResponse | null>(null);
  const [proofPhotoUrl, setProofPhotoUrl] = useState<string | null>(null);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      setTracking(initialTracking);
      if (initialTracking?.currentStatus !== 'DELIVERED') {
        setProofPhotoUrl(null);
      }
    }, 0);

    return () => window.clearTimeout(timeoutId);
  }, [initialTracking]);

  const handleLiveUpdate = (update: ParcelTrackingUpdate) => {
    setTracking((current) => {
      if (!current || update.trackingNumber !== current.trackingNumber) {
        return current;
      }

      const alreadyRecorded = current.timeline.some(
        (entry) =>
          entry.status === update.currentStatus && entry.timestamp === update.timestamp
      );

      return {
        ...current,
        currentStatus: update.currentStatus,
        maskedRecipientName:
          update.recipientNameMasked || current.maskedRecipientName,
        timeline: alreadyRecorded
          ? current.timeline
          : [
              ...current.timeline,
              {
                status: update.currentStatus,
                timestamp: update.timestamp,
                deliveryNote: update.deliveryNote,
              },
            ],
      };
    });

    if (update.proofPhotoUrl) {
      setProofPhotoUrl(update.proofPhotoUrl);
    }
  };

  const connectionStatus = useParcelWebSocket(trackingNumber, handleLiveUpdate);
  const displayProofPhotoUrl = useMemo(
    () => proofPhotoUrl || (tracking?.currentStatus === 'DELIVERED' ? null : null),
    [proofPhotoUrl, tracking?.currentStatus]
  );

  if (loading) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-slate-50 px-6">
        <div className="flex items-center gap-3 text-sm font-semibold text-slate-600">
          <Spinner size="md" />
          Loading parcel tracking information...
        </div>
      </main>
    );
  }

  if (error || !tracking) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-slate-50 px-6">
        <section className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-6 text-center shadow-2xs">
          <div className="mx-auto flex h-11 w-11 items-center justify-center rounded-xl bg-rose-50 text-rose-600">
            <Package className="h-5 w-5" />
          </div>
          <h1 className="mt-4 text-lg font-extrabold text-slate-900">Tracking unavailable</h1>
          <p className="mt-2 text-sm text-slate-500">{error || 'No tracking information was found.'}</p>
          <div className="mt-5 flex justify-center gap-2">
            <button
              type="button"
              onClick={() => void refetchTracking()}
              className="inline-flex items-center gap-2 rounded-xl bg-blue-600 px-4 py-2.5 text-xs font-bold text-white transition-colors hover:bg-blue-700"
            >
              <RefreshCw className="h-3.5 w-3.5" />
              Try again
            </button>
            <Link
              href="/"
              className="inline-flex items-center gap-2 rounded-xl border border-slate-200 px-4 py-2.5 text-xs font-bold text-slate-700 transition-colors hover:bg-slate-50"
            >
              Back home
            </Link>
          </div>
        </section>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-slate-50 px-4 py-8 sm:px-6 sm:py-12">
      <div className="mx-auto max-w-5xl">
        <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <Link
            href="/"
            className="inline-flex w-fit items-center gap-2 text-xs font-semibold text-slate-600 transition-colors hover:text-blue-600"
          >
            <ArrowLeft className="h-4 w-4" />
            Back to SwiftDrop
          </Link>
          <LiveConnectionBadge status={connectionStatus} />
        </div>

        <section className="mb-6 rounded-2xl border border-slate-200/80 bg-white p-6 shadow-2xs sm:p-8">
          <div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <div className="mb-2 inline-flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-blue-600">
                <Package className="h-4 w-4" />
                Public parcel tracking
              </div>
              <h1 className="text-2xl font-extrabold tracking-tight text-slate-900 sm:text-3xl">
                {tracking.trackingNumber}
              </h1>
              <p className="mt-1 text-sm text-slate-500">
                Live delivery status and milestone history
              </p>
            </div>
            <div className="rounded-xl bg-blue-50 px-4 py-3 text-left sm:text-right">
              <p className="text-[10px] font-bold uppercase tracking-wider text-blue-600">Current status</p>
              <p className="mt-1 text-sm font-extrabold text-slate-900">
                {tracking.currentStatus.replaceAll('_', ' ')}
              </p>
            </div>
          </div>

          <div className="mt-6 grid gap-3 text-xs sm:grid-cols-2">
            <div className="flex items-start gap-2 rounded-xl bg-slate-50 p-3">
              <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-blue-600" />
              <div>
                <p className="font-semibold text-slate-500">Route</p>
                <p className="mt-0.5 font-bold text-slate-800">
                  {tracking.originZoneName} to {tracking.destinationZoneName}
                </p>
              </div>
            </div>
            <div className="rounded-xl bg-slate-50 p-3">
              <p className="font-semibold text-slate-500">Recipient</p>
              <p className="mt-0.5 font-bold text-slate-800">
                {tracking.maskedRecipientName} · {tracking.maskedRecipientPhone}
              </p>
            </div>
          </div>
        </section>

        <div className="space-y-6">
          <TrackingMilestoneStepper currentStatus={tracking.currentStatus} />
          <TrackingTimeline entries={tracking.timeline} />

          {displayProofPhotoUrl && tracking.currentStatus === 'DELIVERED' && (
            <section className="rounded-2xl border border-slate-200/80 bg-white p-6 shadow-2xs">
              <div className="mb-4 flex items-center gap-2">
                <ImageIcon className="h-4 w-4 text-emerald-600" />
                <h2 className="text-sm font-bold text-slate-900">Delivery proof</h2>
              </div>
              <img
                src={displayProofPhotoUrl}
                alt="Delivery proof"
                className="max-h-[28rem] w-full rounded-xl object-contain bg-slate-50"
              />
            </section>
          )}
        </div>
      </div>
    </main>
  );
}

export default function TrackingPage() {
  return (
    <Suspense
      fallback={
        <main className="flex min-h-screen items-center justify-center bg-slate-50 px-6">
          <div className="flex items-center gap-3 text-sm font-semibold text-slate-600">
            <Spinner size="md" />
            Loading parcel tracking information...
          </div>
        </main>
      }
    >
      <TrackingPageContent />
    </Suspense>
  );
}
