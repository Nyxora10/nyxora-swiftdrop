'use client';

import React, { createContext, useContext, useState, useCallback } from 'react';
import { CheckCircle2, AlertCircle, Info, AlertTriangle, X } from 'lucide-react';

export type ToastType = 'success' | 'error' | 'warning' | 'info';

export interface ToastItem {
  id: string;
  type: ToastType;
  message: string;
  duration?: number;
}

interface ToastContextType {
  toast: {
    success: (message: string, duration?: number) => void;
    error: (message: string, duration?: number) => void;
    warning: (message: string, duration?: number) => void;
    info: (message: string, duration?: number) => void;
  };
  removeToast: (id: string) => void;
}

const ToastContext = createContext<ToastContextType | undefined>(undefined);

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const addToast = useCallback((type: ToastType, message: string, duration = 4000) => {
    const id = `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    const newToast: ToastItem = { id, type, message, duration };

    setToasts((prev) => [...prev, newToast]);

    if (duration > 0) {
      setTimeout(() => {
        removeToast(id);
      }, duration);
    }
  }, [removeToast]);

  const toast = {
    success: (msg: string, dur?: number) => addToast('success', msg, dur),
    error: (msg: string, dur?: number) => addToast('error', msg, dur),
    warning: (msg: string, dur?: number) => addToast('warning', msg, dur),
    info: (msg: string, dur?: number) => addToast('info', msg, dur),
  };

  return (
    <ToastContext.Provider value={{ toast, removeToast }}>
      {children}
      {/* Toast Notification Container */}
      <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2 max-w-sm w-full pointer-events-none p-4">
        {toasts.map((item) => {
          let bgClass = 'bg-slate-900 text-white border-slate-800';
          let icon = <Info className="w-5 h-5 text-blue-400" />;

          if (item.type === 'success') {
            bgClass = 'bg-emerald-950 text-emerald-50 border-emerald-800/80';
            icon = <CheckCircle2 className="w-5 h-5 text-emerald-400" />;
          } else if (item.type === 'error') {
            bgClass = 'bg-rose-950 text-rose-50 border-rose-800/80';
            icon = <AlertCircle className="w-5 h-5 text-rose-400" />;
          } else if (item.type === 'warning') {
            bgClass = 'bg-amber-950 text-amber-50 border-amber-800/80';
            icon = <AlertTriangle className="w-5 h-5 text-amber-400" />;
          }

          return (
            <div
              key={item.id}
              className={`pointer-events-auto flex items-start gap-3 p-4 rounded-xl shadow-xl border text-sm transition-all duration-300 animate-in slide-in-from-bottom-5 ${bgClass}`}
            >
              <div className="flex-shrink-0 mt-0.5">{icon}</div>
              <div className="flex-1 text-xs font-medium leading-relaxed">{item.message}</div>
              <button
                onClick={() => removeToast(item.id)}
                className="flex-shrink-0 opacity-70 hover:opacity-100 transition-opacity p-0.5"
                aria-label="Close notification"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextType['toast'] {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return context.toast;
}

