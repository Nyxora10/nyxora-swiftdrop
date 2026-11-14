'use client';

import React, { useMemo, useState } from 'react';
import Link from 'next/link';
import { Boxes, ChevronLeft, ChevronRight, RefreshCw, Search, ShieldAlert, UploadCloud } from 'lucide-react';
import AppShell from '@/components/layout/AppShell';
import MerchantOrdersTable from '@/components/merchant/MerchantOrdersTable';
import { MAX_ORDER_PAGE_SIZE, useMerchantOrders } from '@/hooks/useMerchantOrders';
import type { MerchantParcelStatus } from '@/types/merchant';

const STATUS_TABS: { label: string; value: MerchantParcelStatus | null }[] = [
  { label: 'All', value: null },
  { label: 'Created', value: 'CREATED' },
  { label: 'Assigned', value: 'ASSIGNED' },
  { label: 'Picked up', value: 'PICKED_UP' },
  { label: 'In transit', value: 'IN_TRANSIT' },
  { label: 'Out for delivery', value: 'OUT_FOR_DELIVERY' },
  { label: 'Delivered', value: 'DELIVERED' },
  { label: 'Failed', value: 'FAILED' },
  { label: 'Returned', value: 'RETURNED_TO_SENDER' },
];

const PAGE_SIZES = [10, 25, MAX_ORDER_PAGE_SIZE];

export default function MerchantOrdersPage() {
  const [status, setStatus] = useState<MerchantParcelStatus | null>(null);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [query, setQuery] = useState('');
  const { data, loading, error, reload } = useMerchantOrders({ page, size, status });

  const paged = data?.orders;
  const totalPages = paged?.totalPages ?? 0;
  const totalElements = paged?.totalElements ?? 0;

  // Search narrows the page already loaded; status and paging are handled by the server.
  const visibleOrders = useMemo(() => {
    const orders = paged?.content ?? [];
    const q = query.trim().toLowerCase();
    if (!q) return orders;
    return orders.filter(
      (o) =>
        o.trackingNumber.toLowerCase().includes(q) ||
        o.recipientName.toLowerCase().includes(q) ||
        o.recipientPhone.includes(q) ||
        (o.destinationZoneName ?? '').toLowerCase().includes(q),
    );
  }, [paged, query]);

  const changeStatus = (value: MerchantParcelStatus | null) => {
    setStatus(value);
    setPage(0);
  };

  const firstShown = totalElements === 0 ? 0 : page * size + 1;
  const lastShown = Math.min((page + 1) * size, totalElements);

  return (
    <AppShell>
      <div className="space-y-6">
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 bg-white p-6 rounded-2xl border border-slate-200 shadow-xs">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-blue-600 flex items-center justify-center text-white shadow-md shadow-blue-500/20">
              <Boxes className="w-6 h-6" />
            </div>
            <div>
              <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Merchant Orders</h1>
              <p className="text-sm text-slate-500">
                {data ? `${data.totalOrders.toLocaleString()} orders booked in total` : 'Every parcel booked under your account'}
              </p>
            </div>
          </div>
          <Link
            href="/merchant/bulk-upload"
            className="inline-flex items-center gap-2 px-4 py-2.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-xl shadow-md shadow-blue-500/20 transition-all self-start md:self-auto"
          >
            <UploadCloud className="w-4 h-4" />
            Bulk Upload
          </Link>
        </div>

        {error && (
          <div role="alert" className="flex items-start gap-3 p-4 bg-rose-50 border border-rose-200 rounded-2xl text-sm text-rose-800">
            <ShieldAlert className="w-5 h-5 shrink-0 mt-0.5" />
            <p className="flex-1">{error}</p>
            <button onClick={reload} className="text-xs font-semibold text-rose-700 hover:text-rose-900 underline">
              Retry
            </button>
          </div>
        )}

        <div className="flex flex-col gap-3 bg-white p-4 rounded-2xl border border-slate-200 shadow-xs">
          <div className="flex flex-wrap items-center gap-1.5" role="tablist" aria-label="Filter by status">
            {STATUS_TABS.map((tab) => (
              <button
                key={tab.label}
                role="tab"
                aria-selected={status === tab.value}
                onClick={() => changeStatus(tab.value)}
                className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                  status === tab.value ? 'bg-blue-600 text-white shadow-xs' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>
          <div className="relative sm:max-w-sm">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <label htmlFor="order-search" className="sr-only">
              Search this page
            </label>
            <input
              id="order-search"
              type="text"
              placeholder="Search this page by tracking, recipient, phone..."
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              className="w-full pl-9 pr-4 py-2 text-xs bg-slate-50 border border-slate-200 rounded-xl focus:outline-hidden focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all text-slate-800"
            />
          </div>
        </div>

        <div className="bg-white rounded-2xl border border-slate-200 shadow-xs overflow-hidden">
          <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between gap-3">
            <h2 className="text-base font-bold text-slate-900">
              {STATUS_TABS.find((t) => t.value === status)?.label ?? 'All'} orders
            </h2>
            <button
              onClick={reload}
              disabled={loading}
              className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-100 transition-all"
              title="Refresh"
              aria-label="Refresh"
            >
              <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-blue-600' : ''}`} />
            </button>
          </div>

          <MerchantOrdersTable
            orders={visibleOrders}
            loading={loading}
            emptyMessage={
              query.trim()
                ? 'No orders on this page match your search.'
                : status
                  ? 'No orders with this status.'
                  : 'No orders yet. Upload a batch to book your first parcels.'
            }
          />

          <div className="px-6 py-3 border-t border-slate-100 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 text-xs text-slate-500">
            <span>
              {totalElements > 0 ? `Showing ${firstShown}–${lastShown} of ${totalElements.toLocaleString()}` : 'No orders'}
            </span>
            <div className="flex items-center gap-3">
              <label htmlFor="page-size" className="flex items-center gap-2">
                Rows
                <select
                  id="page-size"
                  value={size}
                  onChange={(e) => {
                    setSize(Number(e.target.value));
                    setPage(0);
                  }}
                  className="bg-slate-50 border border-slate-200 rounded-lg px-2 py-1 text-slate-700"
                >
                  {PAGE_SIZES.map((n) => (
                    <option key={n} value={n}>
                      {n}
                    </option>
                  ))}
                </select>
              </label>
              <div className="flex items-center gap-1">
                <button
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={loading || page === 0}
                  className="p-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 disabled:opacity-40"
                  aria-label="Previous page"
                >
                  <ChevronLeft className="w-4 h-4" />
                </button>
                <span className="px-2 tabular-nums">
                  Page {totalPages === 0 ? 0 : page + 1} of {totalPages}
                </span>
                <button
                  onClick={() => setPage((p) => p + 1)}
                  disabled={loading || page + 1 >= totalPages}
                  className="p-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 disabled:opacity-40"
                  aria-label="Next page"
                >
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </AppShell>
  );
}
