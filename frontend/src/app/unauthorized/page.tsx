'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { ShieldX, ArrowLeft, LogOut } from 'lucide-react';
import { authStorage } from '@/lib/auth';

export default function UnauthorizedPage() {
  const router = useRouter();

  const handleLogout = () => {
    authStorage.clearSession();
    router.push('/login');
  };

  const handleGoHome = () => {
    const user = authStorage.getUser();
    if (user?.role === 'ROLE_ADMIN') router.push('/admin/dispatch');
    else if (user?.role === 'ROLE_MERCHANT') router.push('/merchant/dashboard');
    else if (user?.role === 'ROLE_COURIER') router.push('/courier/runsheet');
    else router.push('/customer/book');
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col justify-center items-center px-4 sm:px-6 lg:px-8">
      <div className="max-w-md w-full bg-white p-8 rounded-2xl shadow-lg border border-slate-100 text-center">
        <div className="w-16 h-16 bg-rose-50 text-rose-500 rounded-full flex items-center justify-center mx-auto mb-6 ring-8 ring-rose-50/50">
          <ShieldX className="w-8 h-8" />
        </div>

        <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold bg-rose-100 text-rose-800 uppercase tracking-wider mb-3">
          403 Forbidden
        </span>

        <h1 className="text-2xl font-bold text-slate-900 tracking-tight mb-2">
          Access Denied
        </h1>

        <p className="text-slate-600 text-sm leading-relaxed mb-6">
          You do not have the required permissions or role privileges to view this section of
          the platform.
        </p>

        <div className="space-y-3">
          <button
            onClick={handleGoHome}
            className="w-full flex items-center justify-center py-2.5 px-4 rounded-xl text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 transition-all shadow-sm"
          >
            <ArrowLeft className="w-4 h-4 mr-2" />
            Go to Your Portal
          </button>

          <button
            onClick={handleLogout}
            className="w-full flex items-center justify-center py-2.5 px-4 rounded-xl text-sm font-medium text-slate-700 bg-white border border-slate-200 hover:bg-slate-50 transition-all"
          >
            <LogOut className="w-4 h-4 mr-2 text-slate-500" />
            Sign Out
          </button>
        </div>
      </div>
    </div>
  );
}

