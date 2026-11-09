'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Clock, RefreshCw, LogOut, ShieldCheck, Mail } from 'lucide-react';
import { authStorage } from '@/lib/auth';
import api from '@/lib/api';

export default function PendingApprovalPage() {
  const router = useRouter();
  const [checking, setChecking] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const handleCheckStatus = async () => {
    setChecking(true);
    setMessage(null);
    try {
      const response = await api.get('/api/v1/auth/me');
      const userProfile = response.data;

      if (userProfile.status === 'ACTIVE') {
        const currentUser = authStorage.getUser();
        if (currentUser) {
          authStorage.setUser({ ...currentUser, status: 'ACTIVE' });
        }

        // Redirect to appropriate dashboard
        const role = userProfile.role || currentUser?.role;
        if (role === 'ROLE_MERCHANT') router.push('/merchant/dashboard');
        else if (role === 'ROLE_COURIER') router.push('/courier/runsheet');
        else if (role === 'ROLE_ADMIN') router.push('/admin/dispatch');
        else router.push('/customer/book');
      } else {
        setMessage('Your account is still under administrative review. Please check back shortly.');
      }
    } catch {
      setMessage('Unable to check account status. Please try again.');
    } finally {
      setChecking(false);
    }
  };

  const handleLogout = () => {
    authStorage.clearSession();
    router.push('/login');
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col justify-center items-center px-4 sm:px-6 lg:px-8">
      <div className="max-w-md w-full bg-white p-8 rounded-2xl shadow-lg border border-slate-100 text-center">
        {/* Animated Status Icon */}
        <div className="w-16 h-16 bg-amber-50 text-amber-500 rounded-full flex items-center justify-center mx-auto mb-6 ring-8 ring-amber-50/50">
          <Clock className="w-8 h-8 animate-pulse" />
        </div>

        <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold bg-amber-100 text-amber-800 uppercase tracking-wider mb-3">
          Awaiting Review
        </span>

        <h1 className="text-2xl font-bold text-slate-900 tracking-tight mb-2">
          Account Pending Approval
        </h1>

        <p className="text-slate-600 text-sm leading-relaxed mb-6">
          Thank you for registering with <strong>SwiftDrop</strong>. Your partner profile
          and compliance credentials are currently being reviewed by our administrative team.
        </p>

        <div className="bg-slate-50 p-4 rounded-xl border border-slate-100 mb-6 text-left space-y-2">
          <div className="flex items-center text-xs text-slate-700 font-medium">
            <ShieldCheck className="w-4 h-4 text-emerald-600 mr-2 flex-shrink-0" />
            <span>Document verification in progress</span>
          </div>
          <div className="flex items-center text-xs text-slate-700 font-medium">
            <Clock className="w-4 h-4 text-amber-600 mr-2 flex-shrink-0" />
            <span>Typical verification turnaround: 24–48 hours</span>
          </div>
          <div className="flex items-center text-xs text-slate-700 font-medium">
            <Mail className="w-4 h-4 text-blue-600 mr-2 flex-shrink-0" />
            <span>You will receive an email once approved</span>
          </div>
        </div>

        {message && (
          <div className="mb-4 p-3 rounded-lg text-xs bg-amber-50 border border-amber-200 text-amber-800">
            {message}
          </div>
        )}

        <div className="space-y-3">
          <button
            onClick={handleCheckStatus}
            disabled={checking}
            className="w-full flex items-center justify-center py-2.5 px-4 rounded-xl text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-all shadow-sm disabled:opacity-50"
          >
            <RefreshCw className={`w-4 h-4 mr-2 ${checking ? 'animate-spin' : ''}`} />
            {checking ? 'Checking Status...' : 'Check Approval Status'}
          </button>

          <button
            onClick={handleLogout}
            className="w-full flex items-center justify-center py-2.5 px-4 rounded-xl text-sm font-medium text-slate-700 bg-white border border-slate-200 hover:bg-slate-50 hover:text-slate-900 transition-all"
          >
            <LogOut className="w-4 h-4 mr-2 text-slate-500" />
            Sign Out
          </button>
        </div>
      </div>
    </div>
  );
}

