'use client';

import React from 'react';
import { usePathname } from 'next/navigation';
import { Menu } from 'lucide-react';
import { UserSession } from '@/lib/auth';
import CourierShiftToggle from '@/components/courier/CourierShiftToggle';

interface NavbarProps {
  user: UserSession | null;
  onMenuClick: () => void;
}

export default function Navbar({ user, onMenuClick }: NavbarProps) {
  const pathname = usePathname();

  const getBreadcrumbs = () => {
    if (pathname.startsWith('/admin/pricing')) {
      return {
        section: 'OPERATIONS',
        page: 'LOGISTICS & TARIFF MATRIX',
        color: 'text-blue-600',
      };
    }
    if (pathname.startsWith('/admin/approvals')) {
      return {
        section: 'GOVERNANCE',
        page: 'PARTNER ONBOARDING & COMPLIANCE',
        color: 'text-purple-600',
      };
    }
    if (pathname.startsWith('/admin/dispatch')) {
      return {
        section: 'OPERATIONS',
        page: 'DISPATCH BOARD',
        color: 'text-blue-600',
      };
    }
    if (pathname.startsWith('/admin/reports')) {
      return {
        section: 'ANALYTICS',
        page: 'REPORTS & METRICS',
        color: 'text-indigo-600',
      };
    }
    if (pathname.startsWith('/merchant')) {
      return {
        section: 'MERCHANT PORTAL',
        page: 'B2B MANAGEMENT',
        color: 'text-indigo-600',
      };
    }
    if (pathname.startsWith('/courier')) {
      return {
        section: 'COURIER OPERATIONS',
        page: 'ACTIVE RUNSHEET',
        color: 'text-amber-600',
      };
    }
    if (pathname.startsWith('/customer')) {
      return {
        section: 'CUSTOMER SERVICES',
        page: 'DELIVERY BOOKING',
        color: 'text-emerald-600',
      };
    }
    return {
      section: 'SWIFTDROP',
      page: 'ENTERPRISE PLATFORM',
      color: 'text-slate-600',
    };
  };

  const breadcrumb = getBreadcrumbs();

  return (
    <header className="h-13 bg-white border-b border-slate-200 sticky top-0 z-30 flex items-center justify-between px-6 sm:px-8 shadow-2xs">
      <div className="flex items-center gap-3">
        {/* Mobile drawer toggle */}
        <button
          onClick={onMenuClick}
          className="lg:hidden p-1.5 rounded-lg text-slate-600 hover:text-slate-900 hover:bg-slate-100 cursor-pointer"
          aria-label="Toggle navigation"
        >
          <Menu className="w-5 h-5" />
        </button>

        {/* Dynamic Breadcrumb in the Top Header Bar */}
        <div className="flex items-center gap-2 text-xs font-semibold tracking-wider uppercase">
          <span className="text-slate-500 font-medium">{breadcrumb.section}</span>
          <span className="text-slate-300">/</span>
          <span className="text-slate-800 font-bold">{breadcrumb.page}</span>
        </div>
      </div>

      {/* Right section: courier shift toggle */}
      <div className="flex items-center gap-2">
        {user?.role === 'ROLE_COURIER' && <CourierShiftToggle />}
      </div>
    </header>
  );
}
