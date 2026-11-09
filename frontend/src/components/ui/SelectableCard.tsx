import React from 'react';

export interface SelectableCardProps {
  selected: boolean;
  onClick: () => void;
  title: string;
  badge?: string;
  tag?: string;
  subtitle?: string;
  description?: string;
  footer?: React.ReactNode;
  icon?: React.ReactNode;
  className?: string;
  ariaLabel?: string;
}

export default function SelectableCard({
  selected,
  onClick,
  title,
  badge,
  tag,
  subtitle,
  description,
  footer,
  icon,
  className = '',
  ariaLabel,
}: SelectableCardProps) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      aria-label={ariaLabel || title}
      onClick={onClick}
      className={`p-4 sm:p-5 rounded-xl border text-left transition-all cursor-pointer relative flex flex-col justify-between ${
        selected
          ? 'border-blue-600 bg-blue-50/20 ring-2 ring-blue-600/10'
          : 'border-slate-200 hover:border-slate-300 bg-white'
      } ${className}`}
    >
      {tag && (
        <div className="absolute -top-2.5 right-4 bg-amber-500 text-white text-[9px] font-black uppercase tracking-wider px-2 py-0.5 rounded-full shadow-2xs">
          {tag}
        </div>
      )}

      <div>
        <div className="flex items-center justify-between mb-1">
          <div className="flex items-center gap-2">
            {icon && <span className="shrink-0">{icon}</span>}
            <span className="font-bold text-sm text-slate-900">{title}</span>
          </div>
          {badge && (
            <span
              className={`text-[11px] font-bold px-2 py-0.5 rounded-full transition-colors ${
                selected ? 'bg-blue-600 text-white' : 'bg-slate-100 text-slate-600'
              }`}
            >
              {badge}
            </span>
          )}
        </div>

        {subtitle && (
          <p className="text-xs text-slate-500 font-mono mb-2">{subtitle}</p>
        )}

        {description && (
          <p className="text-[11px] text-slate-400 leading-relaxed mb-3">{description}</p>
        )}
      </div>

      {footer && <div className="mt-auto">{footer}</div>}
    </button>
  );
}

