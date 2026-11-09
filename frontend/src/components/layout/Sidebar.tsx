'use client';

import React from 'react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import {
  Package,
  Search,
  History,
  LayoutDashboard,
  UploadCloud,
  Webhook,
  Truck,
  Users,
  Layers,
  BarChart3,
  Sliders,
  X,
  Boxes,
  LogOut,
  User as UserIcon,
  Shield,
} from 'lucide-react';
import { UserSession, authStorage } from '@/lib/auth';
import api from '@/lib/api';
import { useToast } from './ToastContext';

interface SidebarProps {
  user: UserSession | null;
  isOpen: boolean;
  onClose: () => void;
}

interface NavItem {
  name: string;
  href: string;
  icon: React.ComponentType<{ className?: string }>;
}

export default function Sidebar({ user, isOpen, onClose }: SidebarProps) {
  const pathname = usePathname();
  const router = useRouter();
  const toast = useToast();

  const handleLogout = async () => {
    try {
      await api.post('/api/v1/auth/logout');
    } catch {
      // Ignore network errors
    } finally {
      authStorage.clearSession();
      toast.info('Signed out successfully.');
      router.push('/login');
    }
  };

  // Role-specific navigation items
  const getNavItems = (): NavItem[] => {
    const role = user?.role;

    if (role === 'ROLE_CUSTOMER') {
      return [
        { name: 'Book Delivery', href: '/customer/book', icon: Package },
        { name: 'Track Parcel', href: '/customer/track', icon: Search },
        { name: 'Shipment History', href: '/customer/history', icon: History },
      ];
    }

    if (role === 'ROLE_MERCHANT') {
      return [
        { name: 'B2B Dashboard', href: '/merchant/dashboard', icon: LayoutDashboard },
        { name: 'Bulk Order Ingestion', href: '/merchant/bulk-upload', icon: UploadCloud },
        { name: 'Webhook Management', href: '/merchant/webhooks', icon: Webhook },
        { name: 'Merchant Orders', href: '/merchant/orders', icon: Boxes },
      ];
    }

    if (role === 'ROLE_COURIER') {
      return [
        { name: 'Active Runsheet', href: '/courier/runsheet', icon: Truck },
      ];
    }

    if (role === 'ROLE_ADMIN') {
      return [
        { name: 'Dispatch Board', href: '/admin/dispatch', icon: Sliders },
        { name: 'Partner Approvals', href: '/admin/approvals', icon: Users },
        { name: 'Zones & Pricing Rules', href: '/admin/pricing', icon: Layers },
        { name: 'Reports & Analytics', href: '/admin/reports', icon: BarChart3 },
      ];
    }

    // Default fallback
    return [
      { name: 'Track Parcel', href: '/track', icon: Search },
    ];
  };

  const navItems = getNavItems();

  const getRoleBadge = () => {
    switch (user?.role) {
      case 'ROLE_ADMIN':
        return 'Admin Console';
      case 'ROLE_MERCHANT':
        return 'Merchant Portal';
      case 'ROLE_COURIER':
        return 'Courier Operations';
      default:
        return 'Customer Portal';
    }
  };

  return (
    <>
      {/* Mobile Backdrop Overlay */}
      {isOpen && (
        <div
          className="fixed inset-0 bg-slate-900/50 backdrop-blur-sm z-40 lg:hidden transition-opacity"
          onClick={onClose}
        />
      )}

      {/* Sidebar Container */}
      <aside
        className={`fixed top-0 bottom-0 left-0 z-50 w-64 bg-slate-900 text-slate-100 flex flex-col border-r border-slate-800 transition-transform duration-300 ease-in-out lg:translate-x-0 ${
          isOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        {/* Brand Header */}
        <div className="flex items-center justify-between h-16 px-6 border-b border-slate-800/80">
          <Link href="/" className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-blue-600 flex items-center justify-center font-bold text-white shadow-md shadow-blue-500/20">
              S
            </div>
            <div className="flex flex-col">
              <span className="font-bold tracking-tight text-white leading-none">SwiftDrop</span>
              <span className="text-[10px] text-blue-400 font-medium tracking-wider uppercase mt-1">
                {getRoleBadge()}
              </span>
            </div>
          </Link>
          <button
            onClick={onClose}
            className="lg:hidden p-1 rounded-md text-slate-400 hover:text-white hover:bg-slate-800"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Navigation Items */}
        <nav className="flex-1 px-4 py-6 space-y-1.5 overflow-y-auto">
          <div className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider px-3 mb-2">
            Navigation
          </div>
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = pathname === item.href || pathname.startsWith(item.href + '/');

            return (
              <Link
                key={item.href}
                href={item.href}
                onClick={onClose}
                className={`flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-blue-600 text-white shadow-sm'
                    : 'text-slate-300 hover:bg-slate-800 hover:text-white'
                }`}
              >
                <Icon className={`w-4 h-4 ${isActive ? 'text-white' : 'text-slate-400'}`} />
                <span>{item.name}</span>
              </Link>
            );
          })}
        </nav>

        {/* User Profile & Logout Section (Sidebar Bottom) */}
        {user ? (
          <div className="p-3 border-t border-slate-800 bg-slate-950/60">
            <div className="flex items-center justify-between gap-2 p-2 rounded-xl bg-slate-800/80 border border-slate-700/60 shadow-xs">
              <div className="flex items-center gap-2.5 min-w-0">
                <div className="w-8 h-8 rounded-lg bg-blue-600 border border-blue-400/30 flex items-center justify-center text-white font-bold text-xs shrink-0 shadow-sm">
                  {user.fullName ? user.fullName.charAt(0).toUpperCase() : <UserIcon className="w-4 h-4" />}
                </div>
                <div className="flex flex-col min-w-0">
                  <span className="text-xs font-bold text-white truncate leading-tight">
                    {user.fullName || user.email}
                  </span>
                  <span className="text-[10px] text-slate-400 truncate leading-tight mt-0.5">
                    {user.email}
                  </span>
                  <div className="mt-1">
                    <span className="inline-flex items-center px-1.5 py-0.2 rounded text-[9px] font-bold uppercase tracking-wider bg-purple-500/20 text-purple-300 border border-purple-500/40">
                      {user.role ? user.role.replace('ROLE_', '') : 'USER'}
                    </span>
                  </div>
                </div>
              </div>

              <button
                onClick={handleLogout}
                className="p-1.5 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 transition-colors shrink-0 cursor-pointer"
                title="Sign Out"
              >
                <LogOut className="w-4 h-4" />
              </button>
            </div>
          </div>
        ) : (
          <div className="p-3 border-t border-slate-800">
            <Link
              href="/login"
              className="flex items-center justify-center gap-2 w-full py-2 px-3 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold shadow-sm transition"
            >
              Sign In
            </Link>
          </div>
        )}

        {/* Footer info */}
        <div className="px-4 py-3 border-t border-slate-800/80 text-[10px] text-slate-500 flex justify-between">
          <span>Nyxora SwiftDrop</span>
          <span className="font-mono text-slate-400">Sri Lanka</span>
        </div>
      </aside>
    </>
  );
}

