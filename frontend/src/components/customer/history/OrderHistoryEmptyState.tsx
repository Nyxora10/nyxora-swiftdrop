'use client';

import React from 'react';
import Link from 'next/link';
import { PackageSearch, Plus, RotateCcw } from 'lucide-react';
import Button from '@/components/ui/Button';

interface OrderHistoryEmptyStateProps {
  isFiltered: boolean;
  onClearFilters: () => void;
}

export default function OrderHistoryEmptyState({
  isFiltered,
  onClearFilters,
}: OrderHistoryEmptyStateProps) {
  if (isFiltered) {
    return (
      <div className="bg-white rounded-2xl border border-slate-200/80 p-8 sm:p-12 text-center space-y-4 shadow-2xs">
        <div className="w-12 h-12 rounded-2xl bg-slate-100 border border-slate-200 flex items-center justify-center text-slate-500 mx-auto">
          <PackageSearch className="w-6 h-6" />
        </div>
        <div className="max-w-sm mx-auto space-y-1">
          <h3 className="text-base font-bold text-slate-900">
            No matching shipments found
          </h3>
          <p className="text-xs text-slate-500">
            We couldn&apos;t find any deliveries matching your active search query or status filter.
          </p>
        </div>
        <div className="pt-2">
          <Button
            variant="secondary"
            size="sm"
            onClick={onClearFilters}
            leftIcon={<RotateCcw className="w-3.5 h-3.5" />}
          >
            Clear Search & Filters
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-2xl border border-slate-200/80 p-8 sm:p-12 text-center space-y-4 shadow-2xs">
      <div className="w-12 h-12 rounded-2xl bg-blue-50 border border-blue-200 flex items-center justify-center text-blue-600 mx-auto">
        <PackageSearch className="w-6 h-6" />
      </div>
      <div className="max-w-sm mx-auto space-y-1">
        <h3 className="text-base font-bold text-slate-900">
          No delivery bookings yet
        </h3>
        <p className="text-xs text-slate-500">
          You haven&apos;t placed any parcel orders. Book your first delivery to track milestones and download official receipts.
        </p>
      </div>
      <div className="pt-2">
        <Link href="/customer/book">
          <Button
            variant="primary"
            size="sm"
            leftIcon={<Plus className="w-4 h-4" />}
          >
            Book Your First Delivery
          </Button>
        </Link>
      </div>
    </div>
  );
}

