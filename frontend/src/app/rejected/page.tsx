'use client';

import React, { Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { XCircle, LogOut, AlertCircle, HelpCircle } from 'lucide-react';
import { authStorage, useUserSession } from '@/lib/auth';

function RejectedContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const user = useUserSession();

  // Derive reason without cascading setState-in-effect
  const reason =
    searchParams.get('reason') ||
    user?.statusReason ||
    'Registration details did not meet SwiftDrop verification criteria.';

  const handleLogout = () => {
    authStorage.clearSession();
    router.push('/login');
  };

  return (
    <div className="max-w-md w-full bg-white p-8 rounded-2xl shadow-lg border border-slate-100 text-center">
      {/* Rejection Alert Icon */}
      <div className="w-16 h-16 bg-rose-50 text-rose-500 rounded-full flex items-center justify-center mx-auto mb-6 ring-8 ring-rose-50/50">
        <XCircle className="w-8 h-8" />
      </div>

      <span className="inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold bg-rose-100 text-rose-800 uppercase tracking-wider mb-3">
        Application Declined
      </span>

      <h1 className="text-2xl font-bold text-slate-900 tracking-tight mb-2">
        Registration Rejected
      </h1>

      <p className="text-slate-600 text-sm leading-relaxed mb-6">
        We regret to inform you that your partner onboarding request has been reviewed
        and declined by our administration team.
      </p>

      {/* Specified statusReason Callout */}
      <div className="bg-rose-50/70 border border-rose-200/80 rounded-xl p-4 text-left mb-6">
        <div className="flex items-center text-xs font-semibold text-rose-900 mb-1">
          <AlertCircle className="w-4 h-4 text-rose-600 mr-1.5 flex-shrink-0" />
          <span>Official Rejection Reason</span>
        </div>
        <p className="text-xs text-rose-800 pl-5.5 italic">
          &ldquo;{reason}&rdquo;
        </p>
      </div>

      <div className="bg-slate-50 p-4 rounded-xl border border-slate-100 mb-6 text-left">
        <div className="flex items-start text-xs text-slate-600">
          <HelpCircle className="w-4 h-4 text-slate-400 mr-2 flex-shrink-0 mt-0.5" />
          <span>
            If you believe this decision was made in error or wish to provide updated documentation,
            please reach out to <a href="mailto:support@swiftdrop.lk" className="text-blue-600 underline font-medium">support@swiftdrop.lk</a> with your registration reference.
          </span>
        </div>
      </div>

      <div className="space-y-3">
        <button
          onClick={handleLogout}
          className="w-full flex items-center justify-center py-2.5 px-4 rounded-xl text-sm font-semibold text-white bg-slate-900 hover:bg-slate-800 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-slate-700 transition-all shadow-sm"
        >
          <LogOut className="w-4 h-4 mr-2" />
          Return to Login
        </button>
      </div>
    </div>
  );
}

export default function AccountRejectedPage() {
  return (
    <div className="min-h-screen bg-slate-50 flex flex-col justify-center items-center px-4 sm:px-6 lg:px-8">
      <Suspense fallback={<div className="text-sm text-slate-500">Loading details...</div>}>
        <RejectedContent />
      </Suspense>
    </div>
  );
}
