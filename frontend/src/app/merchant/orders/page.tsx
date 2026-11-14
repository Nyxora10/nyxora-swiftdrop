'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import {
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  Clock3,
  ExternalLink,
  Package,
  RefreshCw,
  Search,
  Truck,
} from 'lucide-react';
import AppShell from '@/components/layout/AppShell';
import { api } from '@/lib/api';

type ParcelStatus =
  | 'CREATED'
  | 'ASSIGNED'
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'FAILED'
  | 'RETURNED_TO_SENDER';

interface MerchantOrder {
  parcelId: string;
  trackingNumber: string;
  currentStatus: ParcelStatus;
  recipientName: string;
  recipientPhone: string;
  dropoffAddress: string;
  originZoneName?: string;
  destinationZoneName?: string;
  weightKg: number;
  parcelSize: 'SMALL' | 'MEDIUM' | 'LARGE';
  estimatedFee: number;
  currency?: string;
  createdAt: string;
  updatedAt: string;
}

interface PagedOrders {
  content: MerchantOrder[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

interface MerchantOrderResponse {
  totalOrders: number;
  inTransitCount: number;
  deliveredCount: number;
  failedCount: number;
  orders: PagedOrders;
}

const PAGE_SIZE = 20;
const statuses: Array<{ value: '' | ParcelStatus; label: string }> = [
  { value: '', label: 'All statuses' },
  { value: 'CREATED', label: 'Created' },
  { value: 'ASSIGNED', label: 'Assigned' },
  { value: 'PICKED_UP', label: 'Picked up' },
  { value: 'IN_TRANSIT', label: 'In transit' },
  { value: 'OUT_FOR_DELIVERY', label: 'Out for delivery' },
  { value: 'DELIVERED', label: 'Delivered' },
  { value: 'FAILED', label: 'Failed' },
  { value: 'RETURNED_TO_SENDER', label: 'Returned' },
];

function apiErrorMessage(error: unknown, fallback: string) {
  const data = (error as { response?: { data?: { detail?: string; message?: string } } })?.response?.data;
  return data?.detail || data?.message || fallback;
}

function statusClasses(status: ParcelStatus) {
  if (status === 'DELIVERED') return 'border-emerald-200 bg-emerald-50 text-emerald-700';
  if (status === 'FAILED' || status === 'RETURNED_TO_SENDER') return 'border-red-200 bg-red-50 text-red-700';
  if (status === 'CREATED') return 'border-amber-200 bg-amber-50 text-amber-700';
  return 'border-blue-200 bg-blue-50 text-blue-700';
}

function labelStatus(status: string) {
  return status.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase());
}

export default function MerchantOrdersPage() {
  const [data, setData] = useState<MerchantOrderResponse | null>(null);
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<'' | ParcelStatus>('');
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const requestOrders = useCallback(() => {
    return api.get<MerchantOrderResponse>('/api/v1/parcels/merchant/orders', {
      params: { page, size: PAGE_SIZE, ...(status ? { status } : {}) },
    });
  }, [page, status]);

  const loadOrders = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await requestOrders();
      setData(response.data);
    } catch (loadError) {
      setData(null);
      setError(apiErrorMessage(loadError, 'Could not load merchant orders.'));
    } finally {
      setLoading(false);
    }
  }, [requestOrders]);

  useEffect(() => {
    let cancelled = false;
    requestOrders()
      .then((response) => {
        if (!cancelled) setData(response.data);
      })
      .catch((loadError) => {
        if (cancelled) return;
        setData(null);
        setError(apiErrorMessage(loadError, 'Could not load merchant orders.'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [requestOrders]);

  const filteredOrders = useMemo(() => {
    const query = search.trim().toLowerCase();
    const orders = data?.orders.content ?? [];
    if (!query) return orders;
    return orders.filter((order) =>
      [order.trackingNumber, order.recipientName, order.recipientPhone, order.dropoffAddress, order.destinationZoneName]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(query)),
    );
  }, [data, search]);

  const changeStatus = (nextStatus: '' | ParcelStatus) => {
    setLoading(true);
    setError(null);
    setStatus(nextStatus);
    setPage(0);
  };

  const changePage = (nextPage: number) => {
    setLoading(true);
    setError(null);
    setPage(nextPage);
  };

  const pageInfo = data?.orders;

  return (
    <AppShell>
      <div className="space-y-6">
        <header className="flex flex-col gap-4 border-b border-slate-200 pb-6 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="mb-2 text-xs font-bold uppercase tracking-[0.18em] text-blue-600">Merchant Portal / Fulfilment</p>
            <h1 className="text-3xl font-black tracking-tight text-slate-950">Merchant Orders</h1>
            <p className="mt-2 text-sm text-slate-600">Monitor every parcel created through the dashboard, CSV ingestion, or M2M API.</p>
          </div>
          <div className="flex items-center gap-3">
            <button onClick={loadOrders} disabled={loading} className="inline-flex items-center gap-2 rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm font-bold text-slate-700 hover:bg-slate-50 disabled:opacity-60">
              <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} /> Refresh
            </button>
            <Link href="/merchant/dashboard" className="inline-flex items-center gap-2 text-sm font-semibold text-slate-600 hover:text-slate-950"><ArrowLeft className="h-4 w-4" /> Dashboard</Link>
          </div>
        </header>

        {error && <div role="alert" className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">{error}</div>}

        <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {[
            { label: 'Total bookings', value: data?.totalOrders ?? 0, icon: Package, color: 'text-slate-700 bg-slate-100' },
            { label: 'In transit', value: data?.inTransitCount ?? 0, icon: Truck, color: 'text-blue-700 bg-blue-50' },
            { label: 'Delivered', value: data?.deliveredCount ?? 0, icon: Package, color: 'text-emerald-700 bg-emerald-50' },
            { label: 'Failed / returned', value: data?.failedCount ?? 0, icon: Clock3, color: 'text-red-700 bg-red-50' },
          ].map(({ label, value, icon: Icon, color }) => (
            <div key={label} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className={`inline-flex rounded-xl p-2.5 ${color}`}><Icon className="h-5 w-5" /></div>
              <p className="mt-4 text-xs font-bold uppercase tracking-wider text-slate-500">{label}</p>
              <p className="mt-1 text-2xl font-black text-slate-950">{loading ? '-' : value}</p>
            </div>
          ))}
        </section>

        <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div className="flex flex-col gap-3 border-b border-slate-200 p-4 md:flex-row md:items-center md:justify-between">
            <div className="flex flex-wrap gap-2">
              {statuses.map((option) => (
                <button
                  key={option.value || 'ALL'}
                  onClick={() => changeStatus(option.value)}
                  className={`rounded-lg px-3 py-2 text-xs font-bold transition ${status === option.value ? 'bg-slate-950 text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}
                >
                  {option.label}
                </button>
              ))}
            </div>
            <label className="relative block min-w-64">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
              <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search this page..." className="w-full rounded-xl border border-slate-300 py-2.5 pl-9 pr-3 text-sm outline-none focus:border-blue-500 focus:ring-4 focus:ring-blue-100" />
            </label>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full min-w-[980px] text-left text-sm">
              <thead className="bg-slate-50 text-xs font-bold uppercase tracking-wider text-slate-500">
                <tr>
                  <th className="px-5 py-3">Tracking</th>
                  <th className="px-5 py-3">Recipient</th>
                  <th className="px-5 py-3">Destination</th>
                  <th className="px-5 py-3">Parcel</th>
                  <th className="px-5 py-3">Fee</th>
                  <th className="px-5 py-3">Status</th>
                  <th className="px-5 py-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {loading && (
                  <tr><td colSpan={7} className="px-5 py-12 text-center text-slate-500"><RefreshCw className="mx-auto mb-2 h-5 w-5 animate-spin" />Loading orders...</td></tr>
                )}
                {!loading && filteredOrders.length === 0 && (
                  <tr><td colSpan={7} className="px-5 py-12 text-center text-slate-500">No orders match the selected filters.</td></tr>
                )}
                {!loading && filteredOrders.map((order) => (
                  <tr key={order.parcelId} className="hover:bg-slate-50/70">
                    <td className="px-5 py-4"><p className="font-mono font-bold text-slate-950">{order.trackingNumber}</p><p className="mt-1 text-xs text-slate-400">{new Date(order.createdAt).toLocaleString()}</p></td>
                    <td className="px-5 py-4"><p className="font-semibold text-slate-900">{order.recipientName}</p><p className="mt-1 text-xs text-slate-500">{order.recipientPhone}</p></td>
                    <td className="max-w-72 px-5 py-4"><p className="truncate font-medium text-slate-800">{order.dropoffAddress}</p><p className="mt-1 text-xs text-slate-500">{order.destinationZoneName || 'Zone not named'}</p></td>
                    <td className="px-5 py-4 text-slate-700">{Number(order.weightKg).toFixed(1)} kg / {order.parcelSize}</td>
                    <td className="px-5 py-4 font-mono font-semibold text-slate-900">{order.currency || 'LKR'} {Number(order.estimatedFee).toFixed(2)}</td>
                    <td className="px-5 py-4"><span className={`inline-flex rounded-full border px-2.5 py-1 text-xs font-bold ${statusClasses(order.currentStatus)}`}>{labelStatus(order.currentStatus)}</span></td>
                    <td className="px-5 py-4 text-right"><Link href={`/track/${order.trackingNumber}`} className="inline-flex items-center gap-1 font-bold text-blue-600 hover:text-blue-700">Track <ExternalLink className="h-3.5 w-3.5" /></Link></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <footer className="flex flex-col gap-3 border-t border-slate-200 px-5 py-4 text-sm text-slate-500 sm:flex-row sm:items-center sm:justify-between">
            <span>{pageInfo ? `${pageInfo.totalElements} matching order${pageInfo.totalElements === 1 ? '' : 's'}` : 'No order data'}</span>
            <div className="flex items-center gap-2">
              <button onClick={() => changePage(Math.max(0, page - 1))} disabled={loading || !pageInfo || pageInfo.first} className="rounded-lg border border-slate-300 p-2 text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40" aria-label="Previous page"><ChevronLeft className="h-4 w-4" /></button>
              <span className="min-w-28 text-center font-semibold text-slate-700">Page {(pageInfo?.pageNumber ?? 0) + 1} of {Math.max(pageInfo?.totalPages ?? 1, 1)}</span>
              <button onClick={() => changePage(page + 1)} disabled={loading || !pageInfo || pageInfo.last} className="rounded-lg border border-slate-300 p-2 text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40" aria-label="Next page"><ChevronRight className="h-4 w-4" /></button>
            </div>
          </footer>
        </section>
      </div>
    </AppShell>
  );
}
