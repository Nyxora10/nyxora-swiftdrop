'use client';

import React from 'react';
import Link from 'next/link';
import { Package, Plus, RefreshCw } from 'lucide-react';
import Button from '@/components/ui/Button';

interface OrderHistoryHeaderProps {
  totalElements: number;
  loading: boolean;
  onRefresh: () => void;
}

export default function OrderHistoryHeader({
  totalElements,
  loading,
  onRefresh,
}: OrderHistoryHeaderProps) {
  return (
    <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-2 border-b border-slate-200/80">
      <div className="space-y-1">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-blue-600/10 border border-blue-600/20 flex items-center justify-center text-blue-600 shrink-0">
            <Package className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-2.5">
              <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
                Shipment History
              </h1>
              <span className="inline-flex items-center px-2 py-0.5 rounded-md text-xs font-semibold bg-slate-100 text-slate-700 border border-slate-200">
                {totalElements} {totalElements === 1 ? 'Shipment' : 'Shipments'}
              </span>
            </div>
            <p className="text-xs sm:text-sm text-slate-500 mt-0.5">
              Track real-time delivery lifecycle, review billing summaries, and download official receipts.
            </p>
          </div>
        </div>
      </div>

      <div className="flex items-center gap-2 self-start sm:self-auto">
        <Button
          variant="secondary"
          size="sm"
          onClick={onRefresh}
          disabled={loading}
          leftIcon={<RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin text-blue-600' : 'text-slate-500'}`} />}
          title="Refresh shipment list"
        >
          <span>Refresh</span>
        </Button>

        <Link href="/customer/book">
          <Button
            variant="primary"
            size="sm"
            leftIcon={<Plus className="w-4 h-4" />}
          >
            <span>Book New Delivery</span>
          </Button>
        </Link>
      </div>
    </div>
  );
}
