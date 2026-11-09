'use client';

import React from 'react';
import Link from 'next/link';
import {
  Package,
  Shield,
  Truck,
  Store,
  ArrowRight,
  Sparkles,
  ExternalLink,
  CheckCircle,
} from 'lucide-react';
import { useUserSession } from '@/lib/auth';

export default function HomePage() {
  const user = useUserSession();

  const getPortalLink = () => {
    if (!user) return '/login';
    switch (user.role) {
      case 'ROLE_ADMIN':
        return '/admin/dispatch';
      case 'ROLE_MERCHANT':
        return '/merchant/dashboard';
      case 'ROLE_COURIER':
        return '/courier/runsheet';
      default:
        return '/customer/book';
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col font-sans">
      {/* Top Bar */}
      <header className="h-16 bg-white border-b border-slate-200 px-6 sm:px-12 flex items-center justify-between shadow-2xs">
        <div className="flex items-center gap-2.5">
          <div className="w-9 h-9 rounded-xl bg-blue-600 text-white font-bold text-lg flex items-center justify-center shadow-md shadow-blue-500/20">
            S
          </div>
          <span className="font-extrabold text-slate-900 tracking-tight text-lg">
            SwiftDrop
          </span>
          <span className="text-xs bg-blue-50 text-blue-700 font-semibold px-2 py-0.5 rounded-full ml-2">
            Enterprise Logistics
          </span>
        </div>

        <div className="flex items-center gap-3">
          {user ? (
            <Link
              href={getPortalLink()}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold bg-blue-600 text-white hover:bg-blue-700 shadow-sm transition-all"
            >
              <span>Open Your Portal</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          ) : (
            <Link
              href="/login"
              className="inline-flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold bg-blue-600 text-white hover:bg-blue-700 shadow-sm transition-all"
            >
              <span>Sign In</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          )}
        </div>
      </header>

      {/* Hero Section */}
      <main className="flex-1 max-w-5xl w-full mx-auto px-6 py-12 sm:py-16">
        <div className="text-center max-w-3xl mx-auto mb-12">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-blue-100/70 text-blue-800 text-xs font-semibold mb-4">
            <Sparkles className="w-3.5 h-3.5 text-blue-600" />
            <span>Cloud-Native Logistics Architecture</span>
          </div>
          <h1 className="text-4xl sm:text-5xl font-extrabold text-slate-950 tracking-tight leading-tight mb-4">
            Next-Gen Courier &amp; Logistics Management
          </h1>
          <p className="text-slate-600 text-base sm:text-lg leading-relaxed">
            High-throughput parcel dispatching, dynamic pricing matrix, real-time STOMP tracking,
            and machine-to-machine B2B integration across Sri Lanka.
          </p>

          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <Link
              href="/login"
              className="inline-flex items-center gap-2 px-6 py-3 rounded-xl text-sm font-semibold bg-slate-900 text-white hover:bg-slate-800 shadow-md transition-all"
            >
              <span>Launch Demo Console</span>
              <ArrowRight className="w-4 h-4" />
            </Link>
          </div>
        </div>

        {/* Feature Grid representing the 10 vertical slices */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-12">
          <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs hover:border-slate-300 transition-all">
            <div className="w-10 h-10 rounded-xl bg-purple-50 text-purple-600 flex items-center justify-center mb-3">
              <Shield className="w-5 h-5" />
            </div>
            <h3 className="font-bold text-sm text-slate-900 mb-1">Perimeter Gateway</h3>
            <p className="text-xs text-slate-500 leading-relaxed">
              Edge JWT validation, header claim relay, and zero-trust RBAC route protection.
            </p>
          </div>

          <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs hover:border-slate-300 transition-all">
            <div className="w-10 h-10 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center mb-3">
              <Truck className="w-5 h-5" />
            </div>
            <h3 className="font-bold text-sm text-slate-900 mb-1">Courier Operations</h3>
            <p className="text-xs text-slate-500 leading-relaxed">
              Deterministic 8-state parcel lifecycle, real-time runsheets, and shift toggles.
            </p>
          </div>

          <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs hover:border-slate-300 transition-all">
            <div className="w-10 h-10 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center mb-3">
              <Store className="w-5 h-5" />
            </div>
            <h3 className="font-bold text-sm text-slate-900 mb-1">Merchant Integration</h3>
            <p className="text-xs text-slate-500 leading-relaxed">
              OAuth 2.0 client credentials, 100-parcel bulk ingestion, and HMAC-signed webhooks.
            </p>
          </div>

          <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs hover:border-slate-300 transition-all">
            <div className="w-10 h-10 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center mb-3">
              <Package className="w-5 h-5" />
            </div>
            <h3 className="font-bold text-sm text-slate-900 mb-1">Customer Experience</h3>
            <p className="text-xs text-slate-500 leading-relaxed">
              Dynamic pricing engine, 4-step booking wizard, live STOMP tracking, and PDF receipts.
            </p>
          </div>
        </div>

        {/* Verification & Holding Screens Testing Bar */}
        <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs">
          <div className="flex items-center justify-between mb-4 pb-3 border-b border-slate-100">
            <div>
              <h2 className="text-sm font-bold text-slate-900">Platform Route Verification</h2>
              <p className="text-xs text-slate-500">Quick links to inspect holding screens and route guards</p>
            </div>
            <span className="flex items-center gap-1.5 text-xs text-emerald-600 font-semibold bg-emerald-50 px-2.5 py-1 rounded-full border border-emerald-200">
              <CheckCircle className="w-3.5 h-3.5" />
              <span>Compiled &amp; Verified</span>
            </span>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
            <Link
              href="/pending-approval"
              className="flex items-center justify-between p-3 rounded-xl bg-slate-50 hover:bg-slate-100 font-medium text-slate-700 transition-colors"
            >
              <span>Pending Approval</span>
              <ExternalLink className="w-3.5 h-3.5 text-slate-400" />
            </Link>
            <Link
              href="/rejected?reason=Insurance%20document%20expired"
              className="flex items-center justify-between p-3 rounded-xl bg-slate-50 hover:bg-slate-100 font-medium text-slate-700 transition-colors"
            >
              <span>Rejected Screen</span>
              <ExternalLink className="w-3.5 h-3.5 text-slate-400" />
            </Link>
            <Link
              href="/suspended"
              className="flex items-center justify-between p-3 rounded-xl bg-slate-50 hover:bg-slate-100 font-medium text-slate-700 transition-colors"
            >
              <span>Suspended Screen</span>
              <ExternalLink className="w-3.5 h-3.5 text-slate-400" />
            </Link>
            <Link
              href="/unauthorized"
              className="flex items-center justify-between p-3 rounded-xl bg-slate-50 hover:bg-slate-100 font-medium text-slate-700 transition-colors"
            >
              <span>403 Forbidden</span>
              <ExternalLink className="w-3.5 h-3.5 text-slate-400" />
            </Link>
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="h-14 border-t border-slate-200 bg-white px-6 sm:px-12 flex items-center justify-between text-xs text-slate-500">
        <span>SwiftDrop Platform &bull; nyxora-swiftdrop</span>
        <span>Spring Boot 3 + Next.js + PostgreSQL + RabbitMQ</span>
      </footer>
    </div>
  );
}
