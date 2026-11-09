'use client';

import React, { useState, useEffect } from 'react';
import { Truck, CheckCircle2, Moon, Loader2 } from 'lucide-react';
import api from '@/lib/api';
import { useToast } from '@/components/layout/ToastContext';

interface CourierProfile {
  vehicleType?: string;
  licensePlate?: string;
  isAvailable?: boolean;
  activeDeliveryCount?: number;
  assignedZoneId?: number;
}

export default function CourierShiftToggle() {
  const toast = useToast();
  const [profile, setProfile] = useState<CourierProfile | null>(null);
  const [isAvailable, setIsAvailable] = useState<boolean>(true);
  const [loading, setLoading] = useState<boolean>(false);
  const [fetching, setFetching] = useState<boolean>(true);

  // Fetch initial courier status
  useEffect(() => {
    let isMounted = true;
    async function fetchCourierProfile() {
      try {
        const response = await api.get('/api/v1/auth/courier/profile');
        if (isMounted && response.data) {
          setProfile(response.data);
          setIsAvailable(response.data.isAvailable ?? true);
        }
      } catch {
        // Fallback default state for offline / initial testing
        if (isMounted) {
          setIsAvailable(true);
        }
      } finally {
        if (isMounted) setFetching(false);
      }
    }

    fetchCourierProfile();
    return () => {
      isMounted = false;
    };
  }, []);

  const handleToggle = async () => {
    const nextState = !isAvailable;
    setIsAvailable(nextState);
    setLoading(true);

    try {
      await api.patch('/api/v1/auth/courier/availability', {
        isAvailable: nextState,
      });

      if (nextState) {
        toast.success('Shift Status: ON DUTY. Ready to receive dispatch assignments.');
      } else {
        toast.info('Shift Status: OFF DUTY. Paused new dispatch assignments.');
      }
    } catch {
      // Revert optimistic state on failure
      setIsAvailable(!nextState);
      toast.error('Failed to update courier shift status. Check gateway connection.');
    } finally {
      setLoading(false);
    }
  };

  if (fetching) {
    return (
      <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-slate-100 text-slate-500 text-xs font-medium">
        <Loader2 className="w-3.5 h-3.5 animate-spin" />
        <span>Loading Shift...</span>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-2 sm:gap-3">
      {/* Workload badge */}
      {profile?.activeDeliveryCount !== undefined && (
        <span className="hidden md:inline-flex items-center px-2 py-0.5 rounded-md text-[11px] font-semibold bg-slate-100 text-slate-700 border border-slate-200">
          <Truck className="w-3 h-3 mr-1 text-slate-500" />
          Load: {profile.activeDeliveryCount}/20
        </span>
      )}

      {/* Interactive Shift Toggle Switch */}
      <button
        onClick={handleToggle}
        disabled={loading}
        title={`Click to set ${isAvailable ? 'Off Duty' : 'On Duty'}`}
        className={`flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-semibold transition-all border shadow-xs cursor-pointer ${
          isAvailable
            ? 'bg-emerald-50 text-emerald-800 border-emerald-300 hover:bg-emerald-100'
            : 'bg-slate-100 text-slate-600 border-slate-300 hover:bg-slate-200'
        }`}
      >
        {loading ? (
          <Loader2 className="w-3.5 h-3.5 animate-spin text-slate-500" />
        ) : isAvailable ? (
          <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
        ) : (
          <Moon className="w-3.5 h-3.5 text-slate-500" />
        )}

        <span>{isAvailable ? 'On Duty' : 'Off Duty'}</span>

        <span
          className={`w-2 h-2 rounded-full ${
            isAvailable ? 'bg-emerald-500 animate-pulse' : 'bg-slate-400'
          }`}
        />
      </button>
    </div>
  );
}

