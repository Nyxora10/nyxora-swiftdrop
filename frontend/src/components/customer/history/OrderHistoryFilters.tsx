'use client';

import React from 'react';
import { Search, X } from 'lucide-react';
import { OrderStatusFilter } from '@/types/order';

interface OrderHistoryFiltersProps {
  statusFilter: OrderStatusFilter;
  onStatusChange: (filter: OrderStatusFilter) => void;
  searchQuery: string;
  onSearchChange: (value: string) => void;
  onClearSearch: () => void;
  pageSize: number;
  onPageSizeChange: (size: number) => void;
  totalFiltered: number;
}

const TABS: Array<{ id: OrderStatusFilter; label: string }> = [
  { id: 'ALL', label: 'All Shipments' },
  { id: 'IN_TRANSIT', label: 'In Transit' },
  { id: 'OUT_FOR_DELIVERY', label: 'Out for Delivery' },
  { id: 'DELIVERED', label: 'Delivered' },
  { id: 'CREATED', label: 'Order Placed' },
  { id: 'FAILED_OR_RETURNED', label: 'Exceptions' },
];

export default function OrderHistoryFilters({
  statusFilter,
  onStatusChange,
  searchQuery,
  onSearchChange,
  onClearSearch,
  pageSize,
  onPageSizeChange,
}: OrderHistoryFiltersProps) {
  return (
    <div className="space-y-3.5">
      {/* Top Bar: Monochrome Segmented Tabs */}
      <div className="flex items-center overflow-x-auto scrollbar-none py-0.5">
        <div className="inline-flex items-center gap-1 p-1 bg-slate-100/90 rounded-xl border border-slate-200/70 text-xs">
          {TABS.map((tab) => {
            const isActive = statusFilter === tab.id;
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => onStatusChange(tab.id)}
                className={`px-3 py-1.5 rounded-lg font-medium transition-all cursor-pointer whitespace-nowrap ${
                  isActive
                    ? 'bg-white text-slate-900 font-semibold shadow-2xs border border-slate-200/80'
                    : 'text-slate-600 hover:text-slate-900 hover:bg-white/50'
                }`}
              >
                {tab.label}
              </button>
            );
          })}
        </div>
      </div>

      {/* Bottom Bar: Search + Page Size */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        {/* Search Bar */}
        <div className="relative flex-1 max-w-md">
          <div className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none">
            <Search className="w-4 h-4" />
          </div>
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder="Search by tracking #, recipient, or zone..."
            className="w-full pl-9 pr-9 py-2 text-xs sm:text-sm bg-white rounded-xl border border-slate-200 text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all shadow-2xs"
          />
          {searchQuery && (
            <button
              type="button"
              onClick={onClearSearch}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 p-0.5 rounded cursor-pointer"
              title="Clear search"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </div>

        {/* Page Size Selector */}
        <div className="flex items-center gap-2 self-end sm:self-auto text-xs text-slate-500">
          <span>Show:</span>
          <select
            value={pageSize}
            onChange={(e) => onPageSizeChange(Number(e.target.value))}
            className="bg-white border border-slate-200 rounded-lg px-2.5 py-1.5 text-xs text-slate-700 font-medium focus:outline-none focus:ring-1 focus:ring-blue-500 cursor-pointer shadow-2xs"
          >
            <option value={10}>10 per page</option>
            <option value={25}>25 per page</option>
            <option value={50}>50 per page</option>
          </select>
        </div>
      </div>
    </div>
  );
}

