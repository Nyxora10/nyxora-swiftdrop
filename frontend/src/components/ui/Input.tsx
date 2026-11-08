import React from 'react';

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  required?: boolean;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
  helperText?: string;
  error?: string;
  isValid?: boolean;
}

export default function Input({
  label,
  required,
  leftIcon,
  rightIcon,
  helperText,
  error,
  isValid,
  id,
  className = '',
  disabled,
  ...props
}: InputProps) {
  const inputId = id || (label ? label.toLowerCase().replace(/\s+/g, '-') : undefined);

  let stateClasses = 'border-slate-200 bg-white text-slate-800 focus:ring-blue-500/20 focus:border-blue-500';
  if (error) {
    stateClasses = 'border-rose-300 bg-rose-50/20 text-rose-900 focus:ring-rose-500/20 focus:border-rose-500';
  } else if (isValid) {
    stateClasses = 'border-emerald-300 bg-emerald-50/20 text-slate-900 focus:ring-emerald-500/20 focus:border-emerald-500';
  }

  return (
    <div className="w-full space-y-1">
      {label && (
        <label htmlFor={inputId} className="block text-xs font-semibold text-slate-700">
          {label} {required && <span className="text-rose-500">*</span>}
        </label>
      )}

      <div className="relative flex items-center">
        {leftIcon && (
          <div className="absolute left-3.5 pointer-events-none text-slate-400">
            {leftIcon}
          </div>
        )}

        <input
          id={inputId}
          disabled={disabled}
          className={`w-full py-2.5 rounded-xl border text-sm placeholder-slate-400 focus:outline-none focus:ring-2 transition-all disabled:bg-slate-50 disabled:text-slate-500 disabled:cursor-not-allowed ${
            leftIcon ? 'pl-10' : 'pl-3.5'
          } ${rightIcon ? 'pr-10' : 'pr-3.5'} ${stateClasses} ${className}`}
          {...props}
        />

        {rightIcon && (
          <div className="absolute right-3.5 pointer-events-none">
            {rightIcon}
          </div>
        )}
      </div>

      {error ? (
        <p className="text-[11px] text-rose-500 font-medium">{error}</p>
      ) : helperText ? (
        <p className="text-[11px] text-slate-400">{helperText}</p>
      ) : null}
    </div>
  );
}

