'use client';

import React from 'react';
import {
  RefreshCw,
  Zap,
  ShieldCheck,
  AlertCircle,
  ArrowLeft,
  CheckCircle2,
} from 'lucide-react';
import { BookingFormData, DeliveryZone, RateQuoteResponse } from '@/types/booking';
import { Button, Spinner } from '@/components/ui';

interface Step3RateQuoteProps {
  formData: BookingFormData;
  quote: RateQuoteResponse | null;
  loading: boolean;
  error: string | null;
  zones: DeliveryZone[];
  onRecalculate: () => void;
  onBack: () => void;
  onConfirm: () => void;
  bookingLoading: boolean;
  onEditSpecs: () => void;
}

export default function Step3RateQuote({
  formData,
  quote,
  loading,
  error,
  zones,
  onRecalculate,
  onBack,
  onConfirm,
  bookingLoading,
  onEditSpecs,
}: Step3RateQuoteProps) {
  const originZone = zones.find((z) => z.id === formData.originZoneId);
  const destZone = zones.find((z) => z.id === formData.destinationZoneId);

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* ================================================================= */}
        {/* LEFT COLUMN: SHIPMENT SUMMARY (Recognition Over Recall)           */}
        {/* ================================================================= */}
        <div className="bg-white rounded-2xl p-6 border border-slate-200/80 shadow-2xs space-y-4">
          <h3 className="text-xs font-bold uppercase tracking-wider text-slate-400">
            Shipment Summary
          </h3>

          <div className="space-y-3 text-xs">
            {/* Route */}
            <div className="pb-3 border-b border-slate-100">
              <span className="text-slate-400 block text-[10px] uppercase font-semibold">Route</span>
              <p className="font-bold text-slate-900 mt-0.5">
                {originZone?.zoneName || 'Origin'} &rarr; {destZone?.zoneName || 'Destination'}
              </p>
            </div>

            {/* Sender & Recipient */}
            <div className="pb-3 border-b border-slate-100">
              <span className="text-slate-400 block text-[10px] uppercase font-semibold">Sender & Recipient</span>
              <p className="font-semibold text-slate-900 mt-0.5">
                {formData.senderName} ({formData.senderPhone})
              </p>
              <p className="text-slate-500 mt-0.5">
                To: {formData.recipientName} ({formData.recipientPhone})
              </p>
            </div>

            {/* Parcel Specs */}
            <div className="pb-3 border-b border-slate-100">
              <span className="text-slate-400 block text-[10px] uppercase font-semibold">Parcel Specifications</span>
              <p className="font-semibold text-slate-900 mt-0.5">
                {formData.weightKg.toFixed(1)} kg &bull; {formData.parcelSize} Size
              </p>
              <p className="text-slate-500 mt-0.5">Speed: {formData.deliveryTier} Service</p>
            </div>

            {formData.specialInstructions && (
              <div>
                <span className="text-slate-400 block text-[10px] uppercase font-semibold">Special Instructions</span>
                <p className="text-slate-600 italic mt-0.5">&ldquo;{formData.specialInstructions}&rdquo;</p>
              </div>
            )}
          </div>

          <button
            type="button"
            onClick={onEditSpecs}
            className="w-full text-center text-xs text-blue-600 font-semibold hover:underline pt-2 cursor-pointer"
          >
            Edit specifications
          </button>
        </div>

        {/* ================================================================= */}
        {/* RIGHT COLUMN: DYNAMIC FEE BREAKDOWN                               */}
        {/* ================================================================= */}
        <div className="lg:col-span-2 bg-white rounded-2xl p-6 sm:p-8 border border-slate-200/80 shadow-2xs space-y-6">
          <div className="flex items-center justify-between pb-4 border-b border-slate-100">
            <div>
              <h2 className="text-base font-extrabold text-slate-900">Instant Rate Quote Breakdown</h2>
              <p className="text-xs text-slate-500">Calculated by the SwiftDrop GoF dynamic pricing engine</p>
            </div>
            <button
              type="button"
              onClick={onRecalculate}
              disabled={loading}
              className="p-2 rounded-xl text-slate-500 hover:bg-slate-100 transition-all cursor-pointer"
              title="Recalculate Rate"
              aria-label="Recalculate Rate"
            >
              <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-blue-600' : ''}`} />
            </button>
          </div>

          {loading ? (
            <div className="py-12 flex flex-col items-center justify-center space-y-3">
              <Spinner size="lg" className="text-blue-600" />
              <p className="text-xs font-semibold text-slate-500">
                Calculating guaranteed tariff with distance matrix...
              </p>
            </div>
          ) : quote ? (
            <div className="space-y-6">
              {/* Itemized Table */}
              <div className="space-y-2.5 text-xs">
                <div className="flex justify-between py-1.5 border-b border-slate-100">
                  <span className="text-slate-600">
                    Base Zone Fare ({originZone?.zoneCode} &rarr; {destZone?.zoneCode})
                  </span>
                  <span className="font-semibold text-slate-900">
                    LKR {Number(quote.baseRate).toFixed(2)}
                  </span>
                </div>

                <div className="flex justify-between py-1.5 border-b border-slate-100">
                  <span className="text-slate-600">
                    Weight Charge ({formData.weightKg.toFixed(1)} kg)
                  </span>
                  <span className="font-semibold text-slate-900">
                    + LKR {Number(quote.weightSurcharge).toFixed(2)}
                  </span>
                </div>

                <div className="flex justify-between py-1.5 border-b border-slate-100">
                  <span className="text-slate-600">
                    Volumetric Size Adjustment ({formData.parcelSize})
                  </span>
                  <span className="font-semibold text-slate-900">
                    + LKR {Number(quote.sizeSurcharge).toFixed(2)}
                  </span>
                </div>

                {formData.deliveryTier === 'EXPRESS' && (
                  <div className="flex justify-between py-1.5 border-b border-slate-100 text-amber-700 bg-amber-50/50 px-2 rounded-lg">
                    <span className="font-medium flex items-center gap-1.5">
                      <Zap className="w-3.5 h-3.5 fill-amber-500 text-amber-500" aria-hidden="true" />
                      Express Priority Surcharge (1.5x)
                    </span>
                    <span className="font-bold">Applied</span>
                  </div>
                )}

                {/* Total Highlights */}
                <div className="flex justify-between items-baseline pt-4 border-t-2 border-slate-900">
                  <div>
                    <span className="text-base font-extrabold text-slate-900">Total Delivery Charge</span>
                    <span className="block text-[10px] text-slate-400">All taxes and fuel surcharges included</span>
                  </div>
                  <div className="text-right">
                    <span className="text-2xl sm:text-3xl font-black text-blue-600">
                      LKR {Number(quote.totalEstimatedFee).toFixed(2)}
                    </span>
                  </div>
                </div>
              </div>

              {/* SLA Guarantee Banner */}
              <div className="bg-emerald-50 border border-emerald-200/80 rounded-xl p-3.5 flex items-center gap-3">
                <ShieldCheck className="w-5 h-5 text-emerald-600 shrink-0" aria-hidden="true" />
                <div className="text-xs">
                  <span className="font-bold text-emerald-900">
                    Guaranteed Delivery SLA: Within {quote.estimatedDeliveryHours} Hours
                  </span>
                  <p className="text-emerald-700 text-[11px]">
                    Real-time status events published to the dispatch queue upon confirmation.
                  </p>
                </div>
              </div>
            </div>
          ) : (
            <div className="bg-rose-50 text-rose-700 p-4 rounded-xl text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{error || 'Failed to obtain rate quote. Please try again.'}</span>
            </div>
          )}
        </div>
      </div>

      {/* Step 3 Actions */}
      <div className="flex justify-between items-center pt-2">
        <Button
          type="button"
          variant="secondary"
          onClick={onBack}
          disabled={bookingLoading}
          leftIcon={<ArrowLeft className="w-3.5 h-3.5" />}
        >
          Back to Specs
        </Button>

        <Button
          type="button"
          onClick={onConfirm}
          disabled={bookingLoading || !quote}
          loading={bookingLoading}
          leftIcon={<CheckCircle2 className="w-4 h-4" />}
          className="shadow-lg shadow-blue-500/25"
        >
          Confirm & Book Delivery
        </Button>
      </div>
    </div>
  );
}
