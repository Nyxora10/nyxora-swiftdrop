'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { ShieldAlert, LogOut, Mail, PhoneCall } from 'lucide-react';
import { authStorage } from '@/lib/auth';

export default function AccountSuspendedPage() {
  const router = useRouter();

  const handleLogout = () => {
    authStorage.clearSession();
    router.push('/login');
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col justify-center items-center px-4 sm:px-6 lg:px-8">
      <div className="max-w-md w-full bg-white p-8 rounded-2xl shadow-lg border border-slate-100 text-center">
        {/* Suspended Alert Icon */}
        <div className="w-16 h-16 bg-red-50 text-red-600 rounded-full flex items-center justify-center mx-auto mb-6 ring-8 ring-red-50/50">
          <ShieldAlert className="w-8 h-8" />
        </div>

        <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold bg-red-100 text-red-800 uppercase tracking-wider mb-3">
          Access Restricted
        </span>

        <h1 className="text-2xl font-bold text-slate-900 tracking-tight mb-2">
          Account Suspended
        </h1>

        <p className="text-slate-600 text-sm leading-relaxed mb-6">
          Your SwiftDrop account has been temporarily suspended by an administrator due
          to policy compliance or account verification review.
        </p>

        <div className="bg-slate-50 p-4 rounded-xl border border-slate-100 mb-6 text-left space-y-2.5">
          <div className="text-xs text-slate-500 font-semibold uppercase tracking-wider">
            Resolution Steps
          </div>
          <div className="flex items-center text-xs text-slate-700 font-medium">
            <Mail className="w-4 h-4 text-slate-500 mr-2 flex-shrink-0" />
            <span>Email: <a href="mailto:compliance@swiftdrop.lk" className="text-blue-600 underline font-medium">compliance@swiftdrop.lk</a></span>
          </div>
          <div className="flex items-center text-xs text-slate-700 font-medium">
            <PhoneCall className="w-4 h-4 text-slate-500 mr-2 flex-shrink-0" />
            <span>Direct Support Hotline: +94 11 234 5678</span>
          </div>
        </div>

        <button
          onClick={handleLogout}
          className="w-full flex items-center justify-center py-2.5 px-4 rounded-xl text-sm font-semibold text-white bg-slate-900 hover:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-slate-700 transition-all shadow-sm"
        >
          <LogOut className="w-4 h-4 mr-2" />
          Sign Out
        </button>
      </div>
    </div>
  );
}

