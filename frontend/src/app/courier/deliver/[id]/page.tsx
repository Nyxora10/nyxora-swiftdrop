'use client';

import React, { Suspense, useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { useRouter, useParams } from 'next/navigation';
import AppShell from '@/components/layout/AppShell';
import SignaturePad from '@/components/courier/SignaturePad';
import {
  getCourierParcelById,
  updateParcelStatus,
  uploadDeliveryProof,
} from '@/services/courierService';
import {
  CourierParcelCard,
  DeliveryFailureReason,
  ParcelStatus,
} from '@/types/courier';
import { useToast } from '@/components/layout/ToastContext';
import {
  ArrowLeft,
  Package,
  Truck,
  MapPin,
  Phone,
  CheckCircle2,
  AlertTriangle,
  Camera,
  Navigation,
  ShieldAlert,
  ExternalLink,
  RefreshCw,
  X,
} from 'lucide-react';

function CourierDeliverPageContent() {
  const router = useRouter();
  const routeParams = useParams();
  const toast = useToast();

  const parcelId = typeof routeParams?.id === 'string' ? routeParams.id : '';

  const [parcel, setParcel] = useState<CourierParcelCard | null>(null);
  const [loading, setLoading] = useState(true);
  const [updating, setUpdating] = useState(false);

  // Delivery Proof Form State
  const [activeAction, setActiveAction] = useState<'DELIVER' | 'FAIL'>('DELIVER');
  const [recipientName, setRecipientName] = useState('');
  const [deliveryNote, setDeliveryNote] = useState('');
  const [signatureBlob, setSignatureBlob] = useState<Blob | null>(null);
  const [photoFile, setPhotoFile] = useState<File | null>(null);
  const [photoPreview, setPhotoPreview] = useState<string | null>(null);
  const [failureReason, setFailureReason] = useState<DeliveryFailureReason>('RECIPIENT_UNAVAILABLE');

  const fetchParcel = useCallback(async () => {
    if (!parcelId) return;
    try {
      const data = await getCourierParcelById(parcelId);
      setParcel(data);
      if (data.recipientName && !recipientName) {
        setRecipientName(data.recipientName);
      }

    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to load parcel details.';
      toast.error(msg);
    } finally {
      setLoading(false);
    }
  }, [parcelId, recipientName, toast]);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      void fetchParcel();
    }, 0);

    return () => window.clearTimeout(timeoutId);
  }, [fetchParcel]);

  // Handle Photo Selection from Camera or Gallery
  const handlePhotoSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (file.size > 5 * 1024 * 1024) {
      toast.error('Photo size exceeds 5MB limit. Please choose a smaller image.');
      return;
    }

    setPhotoFile(file);
    const objectUrl = URL.createObjectURL(file);
    setPhotoPreview(objectUrl);
  };

  const clearPhoto = () => {
    if (photoPreview) URL.revokeObjectURL(photoPreview);
    setPhotoFile(null);
    setPhotoPreview(null);
  };

  // Milestone Status Update (PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY)
  const handleTransition = async (targetStatus: ParcelStatus, note?: string) => {
    if (!parcel) return;
    setUpdating(true);
    try {
      await updateParcelStatus(parcel.id, {
        targetStatus,
        deliveryNote: note || `Transitioned to ${targetStatus}`,
      });
      toast.success(`Parcel transitioned to ${targetStatus}!`);
      await fetchParcel();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Status update failed.';
      toast.error(msg);
    } finally {
      setUpdating(false);
    }
  };

  // Complete Delivery with Proof Upload
  const handleCompleteDelivery = async () => {
    if (!parcel) return;

    if (!recipientName.trim()) {
      toast.error('Recipient name is strictly required.');
      return;
    }

    if (!signatureBlob && !photoFile) {
      toast.error('Delivery proof required: Please provide a recipient signature or doorstep photo.');
      return;
    }

    setUpdating(true);
    try {
      let proofUrl = '';

      // Upload proof file (signature or photo)
      if (photoFile) {
        const uploadRes = await uploadDeliveryProof(parcel.id, photoFile, photoFile.name);
        proofUrl = uploadRes.proofPhotoUrl;
      } else if (signatureBlob) {
        const uploadRes = await uploadDeliveryProof(parcel.id, signatureBlob, `${parcel.id}_signature.png`);
        proofUrl = uploadRes.proofPhotoUrl;
      }

      // Execute DELIVERED status transition
      await updateParcelStatus(parcel.id, {
        targetStatus: 'DELIVERED',
        recipientSignatureName: recipientName.trim(),
        proofPhotoUrl: proofUrl,
        deliveryNote: deliveryNote.trim() || 'Successfully delivered to recipient',
      });

      toast.success('Parcel successfully marked as DELIVERED!');
      router.push('/courier/runsheet');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to complete delivery.';
      toast.error(msg);
    } finally {
      setUpdating(false);
    }
  };

  // Report Delivery Failure
  const handleReportFailure = async () => {
    if (!parcel) return;

    setUpdating(true);
    try {
      await updateParcelStatus(parcel.id, {
        targetStatus: 'FAILED',
        failureReason,
        deliveryNote: deliveryNote.trim() || `Delivery failed: ${failureReason}`,
      });

      toast.warning('Delivery attempt logged as FAILED.');
      await fetchParcel();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to record delivery failure.';
      toast.error(msg);
    } finally {
      setUpdating(false);
    }
  };

  // Stepper milestones
  const steps: { status: ParcelStatus; label: string }[] = [
    { status: 'ASSIGNED', label: 'Assigned' },
    { status: 'PICKED_UP', label: 'Picked Up' },
    { status: 'IN_TRANSIT', label: 'In Transit' },
    { status: 'OUT_FOR_DELIVERY', label: 'Out for Delivery' },
    { status: 'DELIVERED', label: 'Delivered' },
  ];

  const getStepIndex = (status: ParcelStatus) => {
    switch (status) {
      case 'CREATED':
      case 'ASSIGNED':
        return 0;
      case 'PICKED_UP':
        return 1;
      case 'IN_TRANSIT':
        return 2;
      case 'OUT_FOR_DELIVERY':
      case 'FAILED':
        return 3;
      case 'DELIVERED':
      case 'RETURNED_TO_SENDER':
        return 4;
      default:
        return 0;
    }
  };

  const currentStep = parcel ? getStepIndex(parcel.currentStatus) : 0;

  if (loading) {
    return (
      <AppShell>
        <div className="max-w-xl mx-auto p-12 text-center bg-white rounded-2xl border border-slate-200 shadow-2xs mt-8">
          <RefreshCw className="w-8 h-8 mx-auto text-emerald-600 animate-spin mb-3" />
          <p className="text-sm font-medium text-slate-600">Loading delivery details...</p>
        </div>
      </AppShell>
    );
  }

  if (!parcel) {
    return (
      <AppShell>
        <div className="max-w-xl mx-auto p-8 text-center bg-white rounded-2xl border border-slate-200 mt-8 space-y-4">
          <AlertTriangle className="w-10 h-10 text-amber-500 mx-auto" />
          <h2 className="text-lg font-bold text-slate-800">Parcel Not Found</h2>
          <p className="text-xs text-slate-500">
            This parcel could not be loaded or is not assigned to your courier account.
          </p>
          <Link
            href="/courier/runsheet"
            className="inline-flex items-center gap-1.5 px-4 py-2 bg-slate-900 text-white rounded-xl text-xs font-semibold"
          >
            <ArrowLeft className="w-4 h-4" /> Back to Runsheet
          </Link>
        </div>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <div className="max-w-xl mx-auto space-y-4 sm:space-y-6 pb-16">
        {/* Navigation Back Link */}
        <div className="flex items-center justify-between">
          <Link
            href="/courier/runsheet"
            className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-600 hover:text-slate-900 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to Runsheet
          </Link>
          <span className="font-mono text-xs font-bold text-slate-500">
            {parcel.trackingNumber}
          </span>
        </div>

        {/* Milestone Progress Stepper */}
        <div className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200 shadow-2xs">
          <div className="flex items-center justify-between relative">
            <div className="absolute left-0 right-0 top-1/2 -translate-y-1/2 h-1 bg-slate-100 -z-0" />
            {steps.map((s, idx) => {
              const isCompleted = idx < currentStep;
              const isCurrent = idx === currentStep;
              return (
                <div key={s.status} className="flex flex-col items-center relative z-10">
                  <div
                    className={`w-7 h-7 sm:w-8 sm:h-8 rounded-full flex items-center justify-center text-xs font-bold transition-all ${
                      isCompleted
                        ? 'bg-emerald-600 text-white shadow-xs'
                        : isCurrent
                        ? 'bg-slate-900 text-white ring-4 ring-slate-100 shadow-xs'
                        : 'bg-white border-2 border-slate-200 text-slate-400'
                    }`}
                  >
                    {isCompleted ? <CheckCircle2 className="w-4 h-4" /> : idx + 1}
                  </div>
                  <span
                    className={`text-[10px] sm:text-[11px] font-semibold mt-1.5 whitespace-nowrap ${
                      isCurrent ? 'text-slate-900' : 'text-slate-400'
                    }`}
                  >
                    {s.label}
                  </span>
                </div>
              );
            })}
          </div>
        </div>

        {/* Parcel Information Card */}
        <div className="bg-white rounded-2xl p-4 sm:p-6 border border-slate-200 shadow-2xs space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                Current Status
              </span>
              <h2 className="text-lg font-bold text-slate-900">{parcel.currentStatus}</h2>
            </div>
            {parcel.failureCount > 0 && (
              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-bold bg-amber-100 text-amber-800 border border-amber-300">
                <AlertTriangle className="w-3 h-3" />
                Attempt {parcel.failureCount}/3
              </span>
            )}
          </div>

          {/* Recipient Details & Click-to-Call */}
          <div className="p-3.5 bg-slate-50 rounded-xl border border-slate-100 space-y-2 text-xs">
            <div className="flex items-center justify-between">
              <div>
                <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider">
                  Recipient
                </span>
                <p className="font-bold text-slate-800 text-sm">{parcel.recipientName}</p>
              </div>

              {parcel.recipientPhone && (
                <a
                  href={`tel:${parcel.recipientPhone}`}
                  className="flex items-center gap-1.5 px-3 py-1.5 bg-emerald-600 text-white rounded-xl text-xs font-semibold shadow-2xs hover:bg-emerald-700 transition-colors"
                >
                  <Phone className="w-3.5 h-3.5" />
                  Call Recipient
                </a>
              )}
            </div>

            {/* Dropoff Address with Google Maps link */}
            <div className="pt-2 border-t border-slate-200/60 flex items-start justify-between gap-2">
              <div className="flex items-start gap-1.5">
                <MapPin className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
                <div>
                  <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider">
                    Delivery Address
                  </span>
                  <p className="font-medium text-slate-800">{parcel.dropoffAddress}</p>
                </div>
              </div>

              <a
                href={`https://maps.google.com/?q=${encodeURIComponent(parcel.dropoffAddress)}`}
                target="_blank"
                rel="noreferrer"
                className="text-[11px] text-indigo-600 hover:text-indigo-800 font-semibold flex items-center gap-1 shrink-0 pt-0.5"
              >
                Maps <ExternalLink className="w-3 h-3" />
              </a>
            </div>
          </div>

          {/* Package Details */}
          <div className="grid grid-cols-3 gap-2 text-center text-xs">
            <div className="bg-slate-50 p-2 rounded-xl border border-slate-100">
              <span className="text-[10px] text-slate-400 uppercase font-semibold">Weight</span>
              <p className="font-bold text-slate-800">{parcel.weightKg} kg</p>
            </div>
            <div className="bg-slate-50 p-2 rounded-xl border border-slate-100">
              <span className="text-[10px] text-slate-400 uppercase font-semibold">Size</span>
              <p className="font-bold text-slate-800">{parcel.parcelSize}</p>
            </div>
            <div className="bg-slate-50 p-2 rounded-xl border border-slate-100">
              <span className="text-[10px] text-slate-400 uppercase font-semibold">Fee</span>
              <p className="font-bold text-emerald-700">LKR {Number(parcel.estimatedFee).toFixed(2)}</p>
            </div>
          </div>
        </div>

        {/* Intermediate Milestone Transition Actions */}
        {parcel.currentStatus === 'ASSIGNED' && (
          <button
            onClick={() => handleTransition('PICKED_UP', 'Picked up from sender by courier')}
            disabled={updating}
            className="w-full py-3.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-2xl font-bold text-sm flex items-center justify-center gap-2 shadow-sm shadow-indigo-200 transition-colors disabled:opacity-50"
          >
            {updating ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Package className="w-5 h-5" />}
            Confirm Parcel Pickup (PICKED_UP)
          </button>
        )}

        {parcel.currentStatus === 'PICKED_UP' && (
          <button
            onClick={() => handleTransition('IN_TRANSIT', 'Arrived at sorting hub')}
            disabled={updating}
            className="w-full py-3.5 bg-sky-600 hover:bg-sky-700 text-white rounded-2xl font-bold text-sm flex items-center justify-center gap-2 shadow-sm shadow-sky-200 transition-colors disabled:opacity-50"
          >
            {updating ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Truck className="w-5 h-5" />}
            Mark In Transit to Hub (IN_TRANSIT)
          </button>
        )}

        {parcel.currentStatus === 'IN_TRANSIT' && (
          <button
            onClick={() => handleTransition('OUT_FOR_DELIVERY', 'Departed hub for doorstep delivery')}
            disabled={updating}
            className="w-full py-3.5 bg-emerald-600 hover:bg-emerald-700 text-white rounded-2xl font-bold text-sm flex items-center justify-center gap-2 shadow-sm shadow-emerald-200 transition-colors disabled:opacity-50"
          >
            {updating ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Navigation className="w-5 h-5" />}
            Start Last-Mile Delivery (OUT_FOR_DELIVERY)
          </button>
        )}

        {/* Final Execution Mode: When parcel is OUT_FOR_DELIVERY or FAILED */}
        {(parcel.currentStatus === 'OUT_FOR_DELIVERY' || parcel.currentStatus === 'FAILED') && (
          <div className="bg-white rounded-2xl p-4 sm:p-6 border border-slate-200 shadow-2xs space-y-4">
            {/* Mode Switch Tabs */}
            <div className="flex border-b border-slate-200">
              <button
                type="button"
                onClick={() => setActiveAction('DELIVER')}
                className={`flex-1 py-2.5 text-center text-xs font-bold border-b-2 transition-all ${
                  activeAction === 'DELIVER'
                    ? 'border-emerald-600 text-emerald-700'
                    : 'border-transparent text-slate-400 hover:text-slate-600'
                }`}
              >
                Complete Delivery (DELIVERED)
              </button>

              <button
                type="button"
                onClick={() => setActiveAction('FAIL')}
                className={`flex-1 py-2.5 text-center text-xs font-bold border-b-2 transition-all ${
                  activeAction === 'FAIL'
                    ? 'border-rose-600 text-rose-700'
                    : 'border-transparent text-slate-400 hover:text-slate-600'
                }`}
              >
                Report Failure (FAILED)
              </button>
            </div>

            {activeAction === 'DELIVER' ? (
              /* Complete Delivery Panel */
              <div className="space-y-4 pt-1">
                {/* Recipient Name Input */}
                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wide mb-1">
                    Recipient Full Name <span className="text-rose-500">*</span>
                  </label>
                  <input
                    type="text"
                    value={recipientName}
                    onChange={(e) => setRecipientName(e.target.value)}
                    placeholder="e.g. Nimal Perera"
                    className="w-full px-3.5 py-2.5 text-sm bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  />
                </div>

                {/* HTML5 Touch Signature Pad */}
                <SignaturePad onSignatureCapture={setSignatureBlob} disabled={updating} />

                {/* Camera / Photo Capture Input */}
                <div className="space-y-2">
                  <label className="text-xs font-semibold text-slate-700 flex items-center gap-1.5 uppercase tracking-wide">
                    <Camera className="w-3.5 h-3.5 text-emerald-600" />
                    Doorstep Photo Proof (Optional / Alternative)
                  </label>

                  {photoPreview ? (
                    <div className="relative rounded-xl overflow-hidden border border-slate-200 shadow-2xs">
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img
                        src={photoPreview}
                        alt="Proof preview"
                        className="w-full h-44 object-cover"
                      />
                      <button
                        type="button"
                        onClick={clearPhoto}
                        className="absolute top-2 right-2 p-1.5 bg-slate-900/80 hover:bg-slate-900 text-white rounded-full transition-colors"
                        title="Remove photo"
                      >
                        <X className="w-4 h-4" />
                      </button>
                    </div>
                  ) : (
                    <label className="border-2 border-dashed border-slate-300 rounded-xl p-4 flex flex-col items-center justify-center cursor-pointer hover:bg-slate-50 transition-colors">
                      <Camera className="w-6 h-6 text-slate-400 mb-1" />
                      <span className="text-xs font-semibold text-slate-700">
                        Take Photo or Choose File
                      </span>
                      <span className="text-[10px] text-slate-400">PNG, JPEG, WEBP up to 5MB</span>
                      <input
                        type="file"
                        accept="image/*"
                        capture="environment"
                        onChange={handlePhotoSelect}
                        className="hidden"
                      />
                    </label>
                  )}
                </div>

                {/* Delivery Note */}
                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wide mb-1">
                    Courier Delivery Note (Optional)
                  </label>
                  <textarea
                    rows={2}
                    value={deliveryNote}
                    onChange={(e) => setDeliveryNote(e.target.value)}
                    placeholder="e.g. Left with security officer at gate"
                    className="w-full px-3.5 py-2 text-sm bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  />
                </div>

                {/* Submit Delivery Button */}
                <button
                  type="button"
                  onClick={handleCompleteDelivery}
                  disabled={updating}
                  className="w-full py-3.5 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl font-bold text-sm flex items-center justify-center gap-2 shadow-sm shadow-emerald-200 transition-colors disabled:opacity-50"
                >
                  {updating ? (
                    <RefreshCw className="w-4 h-4 animate-spin" />
                  ) : (
                    <CheckCircle2 className="w-4 h-4" />
                  )}
                  Upload Proof & Mark DELIVERED
                </button>
              </div>
            ) : (
              /* Report Failure Panel */
              <div className="space-y-4 pt-1">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wide mb-1">
                    Failure Reason <span className="text-rose-500">*</span>
                  </label>
                  <select
                    value={failureReason}
                    onChange={(e) => setFailureReason(e.target.value as DeliveryFailureReason)}
                    className="w-full px-3.5 py-2.5 text-sm bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-rose-500"
                  >
                    <option value="RECIPIENT_UNAVAILABLE">Recipient Not Available at Doorstep</option>
                    <option value="INCORRECT_ADDRESS">Address Incorrect or Unreachable</option>
                    <option value="CUSTOMER_REJECTED">Recipient Refused to Accept Parcel</option>
                    <option value="SECURITY_ACCESS_RESTRICTED">Gated Community / Security Denied Entry</option>
                    <option value="DAMAGED_PARCEL">Parcel Damaged in Transit</option>
                    <option value="WEATHER_OR_ROAD_BLOCK">Roadblock / Severe Weather Conditions</option>
                    <option value="OTHER">Other Reason</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wide mb-1">
                    Failure Explanation Note
                  </label>
                  <textarea
                    rows={2}
                    value={deliveryNote}
                    onChange={(e) => setDeliveryNote(e.target.value)}
                    placeholder="Details about the failed attempt..."
                    className="w-full px-3.5 py-2 text-sm bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-rose-500"
                  />
                </div>

                {parcel.failureCount >= 2 && (
                  <div className="p-3 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-800 flex items-start gap-2">
                    <ShieldAlert className="w-4 h-4 shrink-0 text-rose-600 mt-0.5" />
                    <div>
                      <span className="font-bold">Threshold Warning:</span>
                      <p>
                        This will be the 3rd failed attempt. Under SwiftDrop logistics policy, this
                        parcel will automatically transition to <strong>RETURNED_TO_SENDER</strong>.
                      </p>
                    </div>
                  </div>
                )}

                <button
                  type="button"
                  onClick={handleReportFailure}
                  disabled={updating}
                  className="w-full py-3.5 bg-rose-600 hover:bg-rose-700 text-white rounded-xl font-bold text-sm flex items-center justify-center gap-2 shadow-sm shadow-rose-200 transition-colors disabled:opacity-50"
                >
                  {updating ? (
                    <RefreshCw className="w-4 h-4 animate-spin" />
                  ) : (
                    <AlertTriangle className="w-4 h-4" />
                  )}
                  Log Delivery Failure (FAILED)
                </button>
              </div>
            )}
          </div>
        )}

        {/* Completed Terminal View */}
        {parcel.currentStatus === 'DELIVERED' && (
          <div className="bg-emerald-50 border border-emerald-200 rounded-2xl p-6 text-center space-y-3">
            <CheckCircle2 className="w-12 h-12 text-emerald-600 mx-auto" />
            <h3 className="font-bold text-emerald-900 text-lg">Delivery Completed</h3>
            <p className="text-xs text-emerald-700">
              Proof of delivery has been uploaded to cloud storage and verified.
            </p>
            <Link
              href="/courier/runsheet"
              className="inline-flex items-center gap-1.5 px-4 py-2 bg-emerald-600 text-white rounded-xl text-xs font-semibold hover:bg-emerald-700 transition-colors shadow-2xs"
            >
              <ArrowLeft className="w-4 h-4" />
              Return to Active Runsheet
            </Link>
          </div>
        )}

        {parcel.currentStatus === 'RETURNED_TO_SENDER' && (
          <div className="bg-slate-100 border border-slate-300 rounded-2xl p-6 text-center space-y-3">
            <ShieldAlert className="w-12 h-12 text-slate-600 mx-auto" />
            <h3 className="font-bold text-slate-900 text-lg">Returned to Sender</h3>
            <p className="text-xs text-slate-600">
              Maximum retry limit exceeded (3 attempts). Parcel returned to hub for sender return.
            </p>
            <Link
              href="/courier/runsheet"
              className="inline-flex items-center gap-1.5 px-4 py-2 bg-slate-900 text-white rounded-xl text-xs font-semibold hover:bg-slate-800 transition-colors shadow-2xs"
            >
              <ArrowLeft className="w-4 h-4" />
              Return to Runsheet
            </Link>
          </div>
        )}
      </div>
    </AppShell>
  );
}

export default function CourierDeliverPage() {
  return (
    <Suspense fallback={null}>
      <CourierDeliverPageContent />
    </Suspense>
  );
}
