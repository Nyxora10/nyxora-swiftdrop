'use client';

import React, { useRef, useState, useEffect, useCallback } from 'react';
import { PenTool, RotateCcw, CheckCircle2 } from 'lucide-react';

interface SignaturePadProps {
  onSignatureCapture: (blob: Blob | null) => void;
  disabled?: boolean;
}

/**
 * Mobile-responsive touch and pointer HTML5 Canvas signature pad.
 * Uses unified Pointer Events for smooth finger, stylus, and mouse signing.
 */
export default function SignaturePad({ onSignatureCapture, disabled = false }: SignaturePadProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [isDrawing, setIsDrawing] = useState(false);
  const [hasSignature, setHasSignature] = useState(false);

  // Initialize canvas context with High-DPI support
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const rect = canvas.getBoundingClientRect();
    const dpr = typeof window !== 'undefined' ? window.devicePixelRatio || 1 : 1;
    canvas.width = rect.width * dpr;
    canvas.height = rect.height * dpr;
    ctx.scale(dpr, dpr);

    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.lineWidth = 2.5;
    ctx.strokeStyle = '#0f172a'; // slate-900
  }, []);

  const exportBlob = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    canvas.toBlob((blob) => {
      onSignatureCapture(blob);
    }, 'image/png');
  }, [onSignatureCapture]);

  const startDrawing = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (disabled) return;
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    ctx.beginPath();
    ctx.moveTo(x, y);
    setIsDrawing(true);
  };

  const draw = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (!isDrawing || disabled) return;
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    ctx.lineTo(x, y);
    ctx.stroke();
    setHasSignature(true);
  };

  const stopDrawing = () => {
    if (!isDrawing) return;
    setIsDrawing(false);
    exportBlob();
  };

  const clearCanvas = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = typeof window !== 'undefined' ? window.devicePixelRatio || 1 : 1;
    ctx.clearRect(0, 0, canvas.width / dpr, canvas.height / dpr);
    setHasSignature(false);
    onSignatureCapture(null);
  };

  return (
    <div className="w-full space-y-2">
      <div className="flex items-center justify-between">
        <label className="text-xs font-semibold text-slate-700 flex items-center gap-1.5 uppercase tracking-wide">
          <PenTool className="w-3.5 h-3.5 text-emerald-600" />
          Recipient Signature
        </label>
        {hasSignature && (
          <button
            type="button"
            onClick={clearCanvas}
            disabled={disabled}
            className="text-xs text-rose-600 hover:text-rose-700 font-medium flex items-center gap-1 transition-colors"
          >
            <RotateCcw className="w-3 h-3" />
            Clear
          </button>
        )}
      </div>

      <div className="relative border-2 border-dashed border-slate-300 rounded-xl bg-white overflow-hidden shadow-inner">
        <canvas
          ref={canvasRef}
          onPointerDown={startDrawing}
          onPointerMove={draw}
          onPointerUp={stopDrawing}
          onPointerLeave={stopDrawing}
          style={{ touchAction: 'none' }}
          className={`w-full h-44 cursor-crosshair ${
            disabled ? 'opacity-50 cursor-not-allowed' : ''
          }`}
        />

        {!hasSignature && (
          <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none text-slate-400 select-none">
            <PenTool className="w-6 h-6 mb-1 opacity-40" />
            <p className="text-xs font-medium">Sign with finger or stylus inside the box</p>
          </div>
        )}

        {hasSignature && (
          <div className="absolute bottom-2 right-2 pointer-events-none flex items-center gap-1 bg-emerald-50 text-emerald-700 border border-emerald-200 px-2 py-0.5 rounded-full text-[10px] font-semibold">
            <CheckCircle2 className="w-3 h-3 text-emerald-600" />
            Signed
          </div>
        )}
      </div>
    </div>
  );
}
