'use client';

import React from 'react';

interface BarcodeGraphicProps {
  code: string;
  className?: string;
  height?: number;
}

/**
 * Reusable Code-128 linear barcode simulator component.
 * Renders high-contrast barcode patterns suitable for waybill displays.
 */
export default function BarcodeGraphic({ code, className = '', height = 48 }: BarcodeGraphicProps) {
  // Generate deterministic bar widths based on input code characters
  const bars = React.useMemo(() => {
    const pattern: number[] = [2, 1, 1, 2, 3, 1, 2, 2, 1, 3, 1, 1, 2, 1, 3, 2];
    for (let i = 0; i < code.length; i++) {
      const charCode = code.charCodeAt(i);
      pattern.push((charCode % 3) + 1);
      pattern.push(((charCode >> 2) % 2) + 1);
    }
    pattern.push(2, 3, 1, 2, 1, 1, 2);
    return pattern;
  }, [code]);

  return (
    <div
      className={`flex flex-col items-center justify-center select-none ${className}`}
      role="img"
      aria-label={`Linear Barcode for tracking number ${code}`}
    >
      <svg
        height={height}
        viewBox={`0 0 ${bars.reduce((a, b) => a + b * 2, 0)} ${height}`}
        className="w-full max-w-[280px]"
        preserveAspectRatio="none"
      >
        {(() => {
          let currentX = 0;
          return bars.map((width, idx) => {
            const isBar = idx % 2 === 0;
            const barWidth = width * 2;
            const x = currentX;
            currentX += barWidth;
            if (!isBar) return null;
            return (
              <rect
                key={idx}
                x={x}
                y={0}
                width={barWidth}
                height={height}
                fill="#0f172a"
              />
            );
          });
        })()}
      </svg>
      <span className="font-mono text-[10px] tracking-widest text-slate-500 mt-1 font-semibold uppercase">
        *{code}*
      </span>
    </div>
  );
}

