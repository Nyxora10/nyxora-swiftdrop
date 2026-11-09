'use client';

import React from 'react';
import { Package, Truck, Zap, Clock, ArrowRight, ArrowLeft } from 'lucide-react';
import { BookingFormData } from '@/types/booking';
import { WEIGHT_PILLS, SIZE_OPTIONS, TIER_OPTIONS } from '@/constants/booking';
import { Button, Input, SelectableCard } from '@/components/ui';

interface Step2ParcelSpecsProps {
  formData: BookingFormData;
  onChange: (updates: Partial<BookingFormData>) => void;
  onBack: () => void;
  onNext: () => void;
}

export default function Step2ParcelSpecs({
  formData,
  onChange,
  onBack,
  onNext,
}: Step2ParcelSpecsProps) {
  const isWeightValid = formData.weightKg >= 0.1 && formData.weightKg <= 30.0;

  return (
    <div className="space-y-6">
      <div className="bg-white rounded-2xl p-6 sm:p-8 border border-slate-200/80 shadow-2xs space-y-8">
        {/* ================================================================= */}
        {/* WEIGHT SELECTION SECTION                                         */}
        {/* ================================================================= */}
        <div>
          <div className="flex items-center justify-between mb-2">
            <label htmlFor="weightSlider" className="text-sm font-bold text-slate-900 flex items-center gap-2">
              <Package className="w-4 h-4 text-blue-600" aria-hidden="true" />
              <span>Estimated Parcel Weight (Kilograms)</span>
            </label>
            <span
              className="text-sm font-extrabold text-blue-600 bg-blue-50 px-3 py-1 rounded-full border border-blue-100"
              aria-live="polite"
            >
              {formData.weightKg.toFixed(1)} kg
            </span>
          </div>
          <p className="text-xs text-slate-500 mb-4">
            Base fare covers up to 1.0 kg. Additional kilograms incur standard weight surcharges.
          </p>

          {/* Quick-select weight pills */}
          <div className="flex flex-wrap gap-2 mb-4" role="group" aria-label="Preset Weight Values">
            {WEIGHT_PILLS.map((w) => (
              <button
                key={w}
                type="button"
                onClick={() => onChange({ weightKg: w })}
                className={`px-3.5 py-1.5 rounded-xl text-xs font-semibold transition-all cursor-pointer ${
                  formData.weightKg === w
                    ? 'bg-blue-600 text-white shadow-sm shadow-blue-500/20'
                    : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
                }`}
              >
                {w.toFixed(1)} kg
              </button>
            ))}
          </div>

          <input
            id="weightSlider"
            type="range"
            min="0.1"
            max="30.0"
            step="0.1"
            value={formData.weightKg}
            onChange={(e) => onChange({ weightKg: parseFloat(e.target.value) || 0.1 })}
            className="w-full accent-blue-600 cursor-pointer h-2 bg-slate-200 rounded-lg"
            aria-valuemin={0.1}
            aria-valuemax={30.0}
            aria-valuenow={formData.weightKg}
            aria-valuetext={`${formData.weightKg} kilograms`}
          />

          <div className="flex justify-between text-[11px] text-slate-400 mt-2 font-mono">
            <span>0.1 kg (Document)</span>
            <span>15.0 kg</span>
            <span>30.0 kg (Max Standard)</span>
          </div>
        </div>

        {/* ================================================================= */}
        {/* PARCEL SIZE CLASSIFICATION                                        */}
        {/* ================================================================= */}
        <div>
          <label className="block text-sm font-bold text-slate-900 mb-1">
            Parcel Size Classification
          </label>
          <p className="text-xs text-slate-500 mb-4">
            Select the dimensional tier closest to your package dimensions.
          </p>

          <div
            className="grid grid-cols-1 md:grid-cols-3 gap-4"
            role="radiogroup"
            aria-label="Parcel Size Selection"
          >
            {SIZE_OPTIONS.map((opt) => (
              <SelectableCard
                key={opt.id}
                selected={formData.parcelSize === opt.id}
                onClick={() => onChange({ parcelSize: opt.id })}
                title={opt.title}
                badge={opt.mult}
                subtitle={opt.dims}
                description={opt.examples}
              />
            ))}
          </div>
        </div>

        {/* ================================================================= */}
        {/* DELIVERY SPEED & SERVICE TIER                                     */}
        {/* ================================================================= */}
        <div>
          <label className="block text-sm font-bold text-slate-900 mb-1">
            Delivery Speed & Service Tier
          </label>
          <p className="text-xs text-slate-500 mb-4">
            Choose your transit urgency. Express parcels receive priority fleet allocation.
          </p>

          <div
            className="grid grid-cols-1 md:grid-cols-2 gap-4"
            role="radiogroup"
            aria-label="Delivery Service Tier"
          >
            {TIER_OPTIONS.map((tier) => (
              <SelectableCard
                key={tier.id}
                selected={formData.deliveryTier === tier.id}
                onClick={() => onChange({ deliveryTier: tier.id })}
                title={tier.title}
                badge={tier.badge}
                tag={tier.tag}
                description={tier.description}
                icon={
                  tier.id === 'EXPRESS' ? (
                    <Zap className="w-4 h-4 text-amber-500 fill-amber-500" />
                  ) : (
                    <Truck className="w-4 h-4 text-slate-600" />
                  )
                }
                footer={
                  <div
                    className={`flex items-center gap-1.5 text-xs font-semibold ${
                      formData.deliveryTier === tier.id ? 'text-blue-700' : 'text-slate-500'
                    }`}
                  >
                    <Clock className="w-3.5 h-3.5" />
                    <span>{tier.transitTime}</span>
                  </div>
                }
              />
            ))}
          </div>
        </div>

        {/* ================================================================= */}
        {/* SPECIAL INSTRUCTIONS                                              */}
        {/* ================================================================= */}
        <div>
          <Input
            id="specialInstructions"
            label="Special Delivery Instructions / Fragile Remarks (Optional)"
            placeholder="e.g. Handle with care, Glass item, Leave at reception desk"
            value={formData.specialInstructions}
            onChange={(e) => onChange({ specialInstructions: e.target.value })}
            maxLength={200}
          />
        </div>
      </div>

      {/* Navigation Controls */}
      <div className="flex items-center justify-between pt-2">
        <Button
          type="button"
          variant="secondary"
          onClick={onBack}
          leftIcon={<ArrowLeft className="w-4 h-4" />}
        >
          Back to Route
        </Button>

        <Button
          type="button"
          onClick={onNext}
          disabled={!isWeightValid}
          rightIcon={<ArrowRight className="w-4 h-4" />}
        >
          Calculate Rate Quote
        </Button>
      </div>
    </div>
  );
}
