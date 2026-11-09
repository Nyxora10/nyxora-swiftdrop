'use client';

import React from 'react';
import { MapPin, Package, Calculator, CheckCircle2, Check } from 'lucide-react';

interface BookingStepperProps {
  currentStep: number;
}

const STEPS = [
  { num: 1, title: 'Route & Contacts', desc: 'Pickup & drop-off', icon: MapPin },
  { num: 2, title: 'Parcel Specs', desc: 'Weight & service tier', icon: Package },
  { num: 3, title: 'Rate Quote', desc: 'Dynamic tariff preview', icon: Calculator },
  { num: 4, title: 'Confirmation', desc: 'Shipping label & receipt', icon: CheckCircle2 },
];

export default function BookingStepper({ currentStep }: BookingStepperProps) {
  return (
    <nav
      aria-label="Booking Progress"
      className="bg-white rounded-2xl p-4 sm:p-5 border border-slate-200/80 shadow-2xs"
    >
      <ol className="grid grid-cols-2 sm:grid-cols-4 gap-3 sm:gap-2">
        {STEPS.map((step) => {
          const IconComponent = step.icon;
          const isActive = currentStep === step.num;
          const isDone = currentStep > step.num;

          return (
            <li
              key={step.num}
              aria-current={isActive ? 'step' : undefined}
              className={`flex items-center gap-3 p-2.5 rounded-xl transition-all ${
                isActive
                  ? 'bg-blue-50/90 border border-blue-200'
                  : isDone
                  ? 'bg-slate-50 border border-slate-100'
                  : 'opacity-50 border border-transparent'
              }`}
            >
              <div
                className={`w-9 h-9 rounded-xl flex items-center justify-center font-bold text-xs shrink-0 transition-all ${
                  isActive
                    ? 'bg-blue-600 text-white shadow-md shadow-blue-500/20'
                    : isDone
                    ? 'bg-emerald-600 text-white'
                    : 'bg-slate-200 text-slate-600'
                }`}
              >
                {isDone ? (
                  <Check className="w-4 h-4 stroke-[3]" aria-hidden="true" />
                ) : (
                  <IconComponent className="w-4 h-4" aria-hidden="true" />
                )}
              </div>
              <div className="min-w-0">
                <p
                  className={`text-xs font-bold truncate ${
                    isActive ? 'text-blue-900' : isDone ? 'text-slate-800' : 'text-slate-500'
                  }`}
                >
                  {step.title}
                </p>
                <p className="text-[10px] text-slate-400 truncate">{step.desc}</p>
              </div>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

