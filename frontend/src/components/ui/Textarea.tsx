import React from 'react';

export interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  required?: boolean;
  helperText?: string;
  error?: string;
}

export default function Textarea({
  label,
  required,
  helperText,
  error,
  id,
  className = '',
  rows = 3,
  disabled,
  ...props
}: TextareaProps) {
  const inputId = id || (label ? label.toLowerCase().replace(/\s+/g, '-') : undefined);

  let stateClasses = 'border-slate-200 bg-white text-slate-800 focus:ring-blue-500/20 focus:border-blue-500';
  if (error) {
    stateClasses = 'border-rose-300 bg-rose-50/20 text-rose-900 focus:ring-rose-500/20 focus:border-rose-500';
  }

  return (
    <div className="w-full space-y-1">
      {label && (
        <label htmlFor={inputId} className="block text-xs font-semibold text-slate-700">
          {label} {required && <span className="text-rose-500">*</span>}
        </label>
      )}

      <textarea
        id={inputId}
        rows={rows}
        disabled={disabled}
        className={`w-full px-3.5 py-2.5 rounded-xl border text-sm placeholder-slate-400 focus:outline-none focus:ring-2 transition-all disabled:bg-slate-50 disabled:text-slate-500 disabled:cursor-not-allowed resize-none ${stateClasses} ${className}`}
        {...props}
      />

      {error ? (
        <p className="text-[11px] text-rose-500 font-medium">{error}</p>
      ) : helperText ? (
        <p className="text-[11px] text-slate-400">{helperText}</p>
      ) : null}
    </div>
  );
}

