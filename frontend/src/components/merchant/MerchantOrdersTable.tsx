'use client';

import React from 'react';
import Link from 'next/link';
import { ExternalLink, RefreshCw } from 'lucide-react';
import { formatLkr, STATUS_LABELS, STATUS_STYLES } from '@/lib/merchant/format';
import type { MerchantOrder } from '@/types/merchant';

interface Props {
  orders: MerchantOrder[];
  loading: boolean;
  emptyMessage: string;
}

/** Merchant shipments table shared by the dashboard and the orders page. */
export default function MerchantOrdersTable({ orders, loading, emptyMessage }: Props) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left border-collapse">
        <thead>
          <tr className="bg-slate-50/75 border-b border-slate-100 text-slate-500 text-xs uppercase font-semibold">
            <th className="py-3 px-6">Tracking Number</th>
            <th className="py-3 px-6">Recipient</th>
            <th className="py-3 px-6">Destination</th>
            <th className="py-3 px-4">Size & Weight</th>
            <th className="py-3 px-4">Fee</th>
            <th className="py-3 px-6">Status</th>
            <th className="py-3 px-4 text-right">Actions</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100 text-sm">
          {loading ? (
            <tr>
              <td colSpan={7} className="py-12 text-center text-slate-400">
                <RefreshCw className="w-6 h-6 animate-spin mx-auto text-blue-600 mb-2" />
                Loading orders...
              </td>
            </tr>
          ) : orders.length === 0 ? (
            <tr>
              <td colSpan={7} className="py-12 text-center text-slate-400">
                {emptyMessage}
              </td>
            </tr>
          ) : (
            orders.map((order) => (
              <tr key={order.parcelId} className="hover:bg-slate-50/60 transition-colors">
                <td className="py-4 px-6 font-mono font-bold text-blue-600 whitespace-nowrap">{order.trackingNumber}</td>
                <td className="py-4 px-6">
                  <div className="font-semibold text-slate-900">{order.recipientName}</div>
                  <div className="text-xs text-slate-400 font-mono">{order.recipientPhone}</div>
                </td>
                <td className="py-4 px-6">
                  <div className="text-slate-800 text-xs font-medium truncate max-w-[200px]" title={order.dropoffAddress}>
                    {order.dropoffAddress}
                  </div>
                  <div className="text-2xs text-slate-400">{order.destinationZoneName || 'Zone not recorded'}</div>
                </td>
                <td className="py-4 px-4 text-xs text-slate-600 whitespace-nowrap">
                  <span className="font-medium">{order.weightKg} kg</span> &bull;{' '}
                  <span className="px-1.5 py-0.5 rounded-md bg-slate-100 text-2xs font-semibold">{order.parcelSize}</span>
                </td>
                <td className="py-4 px-4 whitespace-nowrap">
                  <div className="font-bold text-slate-900">{formatLkr(order.estimatedFee)}</div>
                  {order.discountAmount != null && Number(order.discountAmount) > 0 && (
                    <div className="text-2xs text-emerald-600 font-medium">Saved {formatLkr(order.discountAmount)}</div>
                  )}
                </td>
                <td className="py-4 px-6">
                  <span
                    className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold border whitespace-nowrap ${
                      STATUS_STYLES[order.currentStatus] ?? 'bg-slate-100 text-slate-700 border-slate-200'
                    }`}
                  >
                    {STATUS_LABELS[order.currentStatus] ?? order.currentStatus}
                  </span>
                </td>
                <td className="py-4 px-4 text-right">
                  <Link
                    href={`/track/${encodeURIComponent(order.trackingNumber)}`}
                    target="_blank"
                    className="inline-flex items-center gap-1 text-xs font-semibold text-blue-600 hover:text-blue-800 transition-colors"
                  >
                    Track <ExternalLink className="w-3.5 h-3.5" />
                  </Link>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
