'use client';

import React, { useEffect } from 'react';
import {
  X,
  Copy,
  MapPin,
  Calendar,
  Weight,
  FileText,
  User,
  Phone,
  ArrowRight,
  ShieldCheck,
} from 'lucide-react';
import { OrderSummary } from '@/types/order';
import OrderHistoryStatusBadge from './OrderHistoryStatusBadge';
import Button from '@/components/ui/Button';

interface OrderDetailModalProps {
  order: OrderSummary | null;
  onClose: () => void;
  onCopyTracking: (tracking: string) => void;
  onDownloadReceipt: (order: OrderSummary) => void;
  isDownloadingReceipt: boolean;
}

export default function OrderDetailModal({
  order,
  onClose,
  onCopyTracking,
  onDownloadReceipt,
  isDownloadingReceipt,
}: OrderDetailModalProps) {
  // Close on Escape key press
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    if (order) {
      window.addEventListener('keydown', handleKeyDown);
      document.body.style.overflow = 'hidden';
    }
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = 'unset';
    };
  }, [order, onClose]);

  if (!order) return null;

  const formattedDate = order.createdAt
    ? new Date(order.createdAt).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    : 'N/A';

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs animate-in fade-in duration-150"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-2xl border border-slate-200/90 shadow-2xl max-w-lg w-full overflow-hidden flex flex-col max-h-[90vh] animate-in zoom-in-95 duration-150"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 bg-slate-50/50">
          <div className="space-y-0.5">
            <span className="text-[11px] font-bold tracking-wider text-slate-400 uppercase">
              Shipment Details
            </span>
            <div className="flex items-center gap-2">
              <span className="font-mono text-sm font-bold text-slate-900">
                {order.trackingNumber}
              </span>
              <button
                type="button"
                onClick={() => onCopyTracking(order.trackingNumber)}
                className="p-1 rounded-md text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer"
                title="Copy tracking code"
              >
                <Copy className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-xl text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors cursor-pointer"
            title="Close"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Content Body */}
        <div className="p-6 space-y-6 overflow-y-auto">
          {/* Status & Date */}
          <div className="flex items-center justify-between pb-4 border-b border-slate-100">
            <div>
              <span className="text-[11px] font-medium text-slate-400 block mb-1">
                Lifecycle Status
              </span>
              <OrderHistoryStatusBadge status={order.currentStatus} />
            </div>
            <div className="text-right">
              <span className="text-[11px] font-medium text-slate-400 block mb-1">
                Booked On
              </span>
              <div className="flex items-center gap-1.5 text-xs font-semibold text-slate-700">
                <Calendar className="w-3.5 h-3.5 text-slate-400" />
                <span>{formattedDate}</span>
              </div>
            </div>
          </div>

          {/* Logistics Route */}
          <div className="space-y-2.5">
            <span className="text-[11px] font-bold tracking-wider text-slate-400 uppercase block">
              Logistics Route
            </span>
            <div className="bg-slate-50/80 rounded-xl p-3.5 border border-slate-100 space-y-3">
              <div className="flex items-center gap-2 text-xs font-semibold text-slate-800">
                <span className="px-2 py-0.5 rounded bg-white border border-slate-200 text-slate-700">
                  {order.originZoneName || 'Origin Hub'}
                </span>
                <ArrowRight className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                <span className="px-2 py-0.5 rounded bg-white border border-slate-200 text-slate-900 font-bold">
                  {order.destinationZoneName || 'Destination Hub'}
                </span>
              </div>

              <div className="flex items-start gap-2 pt-2 border-t border-slate-200/60 text-xs text-slate-600">
                <MapPin className="w-3.5 h-3.5 text-slate-400 shrink-0 mt-0.5" />
                <span className="leading-relaxed">
                  {order.dropoffAddress || 'No dropoff address recorded.'}
                </span>
              </div>
            </div>
          </div>

          {/* Recipient Details */}
          <div className="space-y-2.5">
            <span className="text-[11px] font-bold tracking-wider text-slate-400 uppercase block">
              Recipient Contact
            </span>
            <div className="grid grid-cols-2 gap-3">
              <div className="bg-slate-50/80 rounded-xl p-3 border border-slate-100 flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-lg bg-white border border-slate-200 flex items-center justify-center text-slate-500 shrink-0">
                  <User className="w-4 h-4" />
                </div>
                <div className="min-w-0">
                  <span className="text-[10px] text-slate-400 block">Name</span>
                  <span className="text-xs font-bold text-slate-800 truncate block">
                    {order.recipientName || 'N/A'}
                  </span>
                </div>
              </div>

              <div className="bg-slate-50/80 rounded-xl p-3 border border-slate-100 flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-lg bg-white border border-slate-200 flex items-center justify-center text-slate-500 shrink-0">
                  <Phone className="w-4 h-4" />
                </div>
                <div className="min-w-0">
                  <span className="text-[10px] text-slate-400 block">Phone</span>
                  <span className="text-xs font-bold text-slate-800 truncate block">
                    {order.recipientPhone || 'N/A'}
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* Parcel Specifications & Financials */}
          <div className="grid grid-cols-2 gap-3 pt-2">
            <div className="bg-slate-50/80 rounded-xl p-3.5 border border-slate-100 space-y-1">
              <div className="flex items-center gap-1.5 text-xs text-slate-500">
                <Weight className="w-3.5 h-3.5" />
                <span>Package Specs</span>
              </div>
              <div className="text-sm font-bold text-slate-800">
                {Number(order.weightKg).toFixed(2)} kg
                <span className="text-xs font-medium text-slate-500 ml-1.5">
                  ({order.parcelSize})
                </span>
              </div>
            </div>

            <div className="bg-slate-50/80 rounded-xl p-3.5 border border-slate-100 space-y-1">
              <div className="flex items-center gap-1.5 text-xs text-slate-500">
                <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />
                <span>Total Settlement</span>
              </div>
              <div className="text-base font-bold text-slate-900">
                {order.currency || 'LKR'}{' '}
                {Number(order.estimatedFee).toLocaleString(undefined, {
                  minimumFractionDigits: 2,
                  maximumFractionDigits: 2,
                })}
              </div>
            </div>
          </div>
        </div>

        {/* Footer Actions */}
        <div className="flex items-center justify-between px-6 py-4 border-t border-slate-100 bg-slate-50/50">
          <Button variant="secondary" size="sm" onClick={onClose}>
            Close
          </Button>

          <Button
            variant="primary"
            size="sm"
            onClick={() => onDownloadReceipt(order)}
            loading={isDownloadingReceipt}
            leftIcon={<FileText className="w-4 h-4" />}
          >
            Download PDF Receipt
          </Button>
        </div>
      </div>
    </div>
  );
}
