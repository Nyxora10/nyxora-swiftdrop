import React, { useMemo } from 'react';
import { CheckCircle2, AlertCircle } from 'lucide-react';
import { SRI_LANKAN_PHONE_REGEX } from '@/constants/booking';

export interface PhoneInputProps {
  id?: string;
  label?: string;
  required?: boolean;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  helperText?: string;
  disabled?: boolean;
}

export default function PhoneInput({
  id = 'phone-input',
  label = 'Phone Number (Sri Lanka)',
  required = true,
  value,
  onChange,
  placeholder = '07XXXXXXXX',
  helperText = 'Must be 10 digits starting with 07.',
  disabled = false,
}: PhoneInputProps) {
  const isValid = useMemo(() => SRI_LANKAN_PHONE_REGEX.test(value.trim()), [value]);
  const hasValue = value.length > 0;

  let stateClasses = 'border-slate-200 bg-white focus:border-blue-500 focus:ring-blue-500/20';
  if (hasValue) {
    stateClasses = isValid
      ? 'border-emerald-300 bg-emerald-50/20 focus:border-emerald-500 focus:ring-emerald-500/20'
      : 'border-rose-300 bg-rose-50/20 focus:border-rose-500 focus:ring-rose-500/20';
  }

  return (
    <div className="w-full space-y-1">
      {label && (
        <label htmlFor={id} className="block text-xs font-semibold text-slate-700">
          {label} {required && <span className="text-rose-500">*</span>}
        </label>
      )}

      <div className="relative">
        <input
          id={id}
          type="tel"
          disabled={disabled}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          maxLength={12}
          className={`w-full px-3.5 py-2.5 rounded-xl border text-sm text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 transition-all disabled:bg-slate-50 disabled:text-slate-500 disabled:cursor-not-allowed ${stateClasses}`}
        />

        <div className="absolute right-3 top-3 pointer-events-none">
          {hasValue &&
            (isValid ? (
              <CheckCircle2 className="w-4 h-4 text-emerald-500" aria-label="Valid phone number" />
            ) : (
              <AlertCircle className="w-4 h-4 text-rose-500" aria-label="Invalid phone number" />
            ))}
        </div>
      </div>

      {helperText && (
        <p className="text-[11px] text-slate-400">{helperText}</p>
      )}
    </div>
  );
}

