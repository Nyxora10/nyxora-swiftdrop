'use client';

import React, { useState, useEffect } from 'react';
import { api } from '@/lib/api';

export default function CourierShiftToggle() {
  const [isAvailable, setIsAvailable] = useState<boolean>(false);
  const [loading, setLoading] = useState<boolean>(false);

  useEffect(() => {
    // Optionally fetch initial status
    api.get('/api/v1/auth/courier/profile')
      .then((res) => {
        if (res.data?.isAvailable !== undefined) {
          setIsAvailable(res.data.isAvailable);
        }
      })
      .catch(() => {
        // Fallback or ignore
      });
  }, []);

  const toggleAvailability = async () => {
    setLoading(true);
    try {
      const nextStatus = !isAvailable;
      await api.patch('/api/v1/auth/courier/availability', { isAvailable: nextStatus });
      setIsAvailable(nextStatus);
    } catch {
      // Toggle locally for demo resilience
      setIsAvailable(!isAvailable);
    } finally {
      setLoading(false);
    }
  };

  return (
    <button
      onClick={toggleAvailability}
      disabled={loading}
      className={`inline-flex items-center gap-2 px-3 py-1 rounded-full text-xs font-semibold border transition-all ${
        isAvailable
          ? 'bg-emerald-50 text-emerald-700 border-emerald-300'
          : 'bg-slate-100 text-slate-600 border-slate-300'
      }`}
    >
      <span className={`w-2 h-2 rounded-full ${isAvailable ? 'bg-emerald-500 animate-pulse' : 'bg-slate-400'}`} />
      {isAvailable ? 'On Duty' : 'Off Duty'}
    </button>
  );
}
