import React from 'react';

export interface SelectOption {
  value: string;
  label: string;
}

export interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  required?: boolean;
  options: SelectOption[];
  leftIcon?: React.ReactNode;
  helperText?: string;
  error?: string;
  loading?: boolean;
}

export default function Select({
  label,
  required,
  options,
  leftIcon,
  helperText,
  error,
  loading,
  id,
  className = '',
  disabled,
  ...props
}: SelectProps) {
  const selectId = id || (label ? label.toLowerCase().replace(/\s+/g, '-') : undefined);

  let stateClasses = 'border-slate-200 bg-white text-slate-800 focus:ring-blue-500/20 focus:border-blue-500';
  if (error) {
    stateClasses = 'border-rose-300 bg-rose-50/20 text-rose-900 focus:ring-rose-500/20 focus:border-rose-500';
  }

  return (
    <div className="w-full space-y-1">
      {label && (
        <label htmlFor={selectId} className="block text-xs font-semibold text-slate-700">
          {label} {required && <span className="text-rose-500">*</span>}
        </label>
      )}

      <div className="relative flex items-center">
        {leftIcon && (
          <div className="absolute left-3.5 pointer-events-none text-slate-400">
            {leftIcon}
          </div>
        )}

        <select
          id={selectId}
          disabled={disabled || loading}
          className={`w-full py-2.5 pr-8 rounded-xl border text-sm focus:outline-none focus:ring-2 transition-all appearance-none cursor-pointer disabled:bg-slate-50 disabled:text-slate-500 disabled:cursor-not-allowed ${
            leftIcon ? 'pl-10' : 'pl-3.5'
          } ${stateClasses} ${className}`}
          {...props}
        >
          {loading ? (
            <option value="">Loading logistics hubs...</option>
          ) : (
            options.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))
          )}
        </select>

        {/* Custom Chevron Indicator */}
        <div className="absolute right-3 pointer-events-none text-slate-400">
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 9l-7 7-7-7" />
          </svg>
        </div>
      </div>

      {error ? (
        <p className="text-[11px] text-rose-500 font-medium">{error}</p>
      ) : helperText ? (
        <p className="text-[11px] text-slate-400">{helperText}</p>
      ) : null}
    </div>
  );
}

