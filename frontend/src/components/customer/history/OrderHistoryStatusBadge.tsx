import React from 'react';
import {
  Clock,
  UserCheck,
  PackageCheck,
  Truck,
  Navigation,
  CheckCircle2,
  AlertTriangle,
  RotateCcw,
} from 'lucide-react';
import { ParcelStatus } from '@/types/order';

interface OrderHistoryStatusBadgeProps {
  status: ParcelStatus;
  className?: string;
}

/**
 * Enterprise-grade restrained status badge for parcel lifecycle.
 * Uses a disciplined 4-tier semantic palette:
 * - Neutral Slate: Pending / Initial (CREATED)
 * - Active Logistics Blue: In-Flight (ASSIGNED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY)
 * - Success Emerald: Completed (DELIVERED)
 * - Exception Rose: Failures / Returns (FAILED, RETURNED_TO_SENDER)
 */
export default function OrderHistoryStatusBadge({
  status,
  className = '',
}: OrderHistoryStatusBadgeProps) {
  switch (status) {
    case 'CREATED':
      return (
        <span
          className={`inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-semibold rounded-full border border-slate-200 bg-slate-50 text-slate-700 ${className}`}
        >
          <span className="w-1.5 h-1.5 rounded-full bg-slate-400 shrink-0" />
          <Clock className="w-3 h-3 text-slate-500 shrink-0" />
          <span>Order Placed</span>
        </span>
      );

    case 'ASSIGNED':
      return (
        <span
          className={`inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-semibold rounded-full border border-blue-200/80 bg-blue-50/70 text-blue-700 ${className}`}
        >
          <span className="w-1.5 h-1.5 rounded-full bg-blue-500 shrink-0" />
          <UserCheck className="w-3 h-3 text-blue-600 shrink-0" />
          <span>Assigned</span>
        </span>
      );

    case 'PICKED_UP':
      return (
        <span
          className={`inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-semibold rounded-full border border-blue-200/80 bg-blue-50/70 text-blue-700 ${className}`}
        >
          <span className="w-1.5 h-1.5 rounded-full bg-blue-500 shrink-0" />
          <PackageCheck className="w-3 h-3 text-blue-600 shrink-0" />
          <span>Picked Up</span>
        </span>
      );

    case 'IN_TRANSIT':
      return (
        <span
          className={`inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-semibold rounded-full border border-blue-200/80 bg-blue-50/70 text-blue-700 ${className}`}
        >
          <span className="w-1.5 h-1.5 rounded-full bg-blue-600 animate-pulse shrink-0" />
          <Truck className="w-3 h-3 text-blue-600 shrink-0" />
          <span>In Transit</span>
        </span>
      );

    case 'OUT_FOR_DELIVERY':
      return (
        <span
          className={`inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-semibold rounded-full border border-blue-300 bg-blue-50 text-blue-800 ${className}`}
        >
          <span className="w-1.5 h-1.5 rounded-full bg-blue-600 animate-pulse shrink-0" />
          <Navigation className="w-3 h-3 text-blue-700 shrink-0" />
          <span>Out for Delivery</span>
        </span>
      );

    case 'DELIVERED':
      return (
        <span
          className={`inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-semibold rounded-full border border-emerald-200/80 bg-emerald-50 text-emerald-800 ${className}`}
        >
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 shrink-0" />
          <CheckCircle2 className="w-3 h-3 text-emerald-600 shrink-0" />
          <span>Delivered</span>
        </span>
      );

    case 'FAILED':
      return (
        <span
          className={`inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-semibold rounded-full border border-rose-200/80 bg-rose-50 text-rose-800 ${className}`}
        >
          <span className="w-1.5 h-1.5 rounded-full bg-rose-500 shrink-0" />
          <AlertTriangle className="w-3 h-3 text-rose-600 shrink-0" />
          <span>Delivery Failed</span>
        </span>
      );

    case 'RETURNED_TO_SENDER':
      return (
        <span
          className={`inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-semibold rounded-full border border-rose-200/80 bg-rose-50 text-rose-800 ${className}`}
        >
          <span className="w-1.5 h-1.5 rounded-full bg-rose-500 shrink-0" />
          <RotateCcw className="w-3 h-3 text-rose-600 shrink-0" />
          <span>Returned to Sender</span>
        </span>
      );

    default:
      return (
        <span
          className={`inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-semibold rounded-full border border-slate-200 bg-slate-50 text-slate-700 ${className}`}
        >
          <span className="w-1.5 h-1.5 rounded-full bg-slate-400 shrink-0" />
          <span>{status}</span>
        </span>
      );
  }
}
