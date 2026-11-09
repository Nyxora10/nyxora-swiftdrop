'use client';

import React from 'react';
import {
  Copy,
  ArrowRight,
  Eye,
  FileDown,
  Calendar,
} from 'lucide-react';
import { OrderSummary } from '@/types/order';
import OrderHistoryStatusBadge from './OrderHistoryStatusBadge';
import Spinner from '@/components/ui/Spinner';

interface OrderHistoryTableProps {
  orders: OrderSummary[];
  onCopyTracking: (tracking: string) => void;
  onOpenDetail: (order: OrderSummary) => void;
  onDownloadReceipt: (order: OrderSummary) => void;
  receiptLoadingMap: Record<string, boolean>;
}

export default function OrderHistoryTable({
  orders,
  onCopyTracking,
  onOpenDetail,
  onDownloadReceipt,
  receiptLoadingMap,
}: OrderHistoryTableProps) {
  return (
    <div className="space-y-4">
      {/* 1. Desktop Tabular View (hidden md:block) */}
      <div className="hidden md:block bg-white rounded-2xl border border-slate-200/80 shadow-2xs overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="border-b border-slate-100 bg-slate-50/75 text-[11px] font-bold uppercase tracking-wider text-slate-400">
                <th className="py-3.5 pl-6 pr-4">Tracking #</th>
                <th className="py-3.5 px-4">Recipient & Route</th>
                <th className="py-3.5 px-4">Specs</th>
                <th className="py-3.5 px-4">Status</th>
                <th className="py-3.5 px-4">Settlement Fee</th>
                <th className="py-3.5 px-4">Booked Date</th>
                <th className="py-3.5 pr-6 pl-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-xs">
              {orders.map((order) => {
                const isDownloading = !!receiptLoadingMap[order.parcelId];
                const dateDisplay = order.createdAt
                  ? new Date(order.createdAt).toLocaleDateString('en-US', {
                      month: 'short',
                      day: 'numeric',
                      year: 'numeric',
                    })
                  : '—';

                return (
                  <tr
                    key={order.parcelId}
                    className="hover:bg-slate-50/70 transition-colors group"
                  >
                    {/* Tracking Number */}
                    <td className="py-4 pl-6 pr-4 whitespace-nowrap">
                      <div className="flex items-center gap-2">
                        <span className="font-mono font-bold text-slate-900 group-hover:text-blue-600 transition-colors">
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
                    </td>

                    {/* Recipient & Route */}
                    <td className="py-4 px-4 max-w-xs">
                      <div className="space-y-0.5">
                        <div className="font-semibold text-slate-900 truncate">
                          {order.recipientName}
                        </div>
                        <div className="flex items-center gap-1.5 text-slate-500 text-[11px]">
                          <span className="truncate">{order.originZoneName}</span>
                          <ArrowRight className="w-3 h-3 text-slate-400 shrink-0" />
                          <span className="font-medium text-slate-700 truncate">
                            {order.destinationZoneName}
                          </span>
                        </div>
                      </div>
                    </td>

                    {/* Specs */}
                    <td className="py-4 px-4 whitespace-nowrap">
                      <span className="inline-flex items-center px-2 py-0.5 rounded-md text-[11px] font-medium bg-slate-100 text-slate-600 border border-slate-200/60">
                        {Number(order.weightKg).toFixed(1)} kg • {order.parcelSize}
                      </span>
                    </td>

                    {/* Status Badge */}
                    <td className="py-4 px-4 whitespace-nowrap">
                      <OrderHistoryStatusBadge status={order.currentStatus} />
                    </td>

                    {/* Fee */}
                    <td className="py-4 px-4 whitespace-nowrap font-bold text-slate-900">
                      {order.currency || 'LKR'}{' '}
                      {Number(order.estimatedFee).toLocaleString(undefined, {
                        minimumFractionDigits: 2,
                        maximumFractionDigits: 2,
                      })}
                    </td>

                    {/* Booked Date */}
                    <td className="py-4 px-4 whitespace-nowrap text-slate-500">
                      {dateDisplay}
                    </td>

                    {/* Quick Actions */}
                    <td className="py-4 pr-6 pl-4 whitespace-nowrap text-right">
                      <div className="flex items-center justify-end gap-1.5">
                        <button
                          type="button"
                          onClick={() => onOpenDetail(order)}
                          className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-xs font-semibold text-slate-600 hover:text-slate-900 hover:bg-slate-100 transition-colors cursor-pointer"
                          title="View order details"
                        >
                          <Eye className="w-3.5 h-3.5" />
                          <span>Details</span>
                        </button>

                        <button
                          type="button"
                          onClick={() => onDownloadReceipt(order)}
                          disabled={isDownloading}
                          className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-xs font-semibold text-blue-600 hover:text-blue-700 hover:bg-blue-50 transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                          title="Download PDF delivery receipt"
                        >
                          {isDownloading ? (
                            <Spinner size="sm" className="text-blue-600" />
                          ) : (
                            <FileDown className="w-3.5 h-3.5" />
                          )}
                          <span>PDF</span>
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* 2. Mobile Responsive Cards (md:hidden) */}
      <div className="md:hidden space-y-3">
        {orders.map((order) => {
          const isDownloading = !!receiptLoadingMap[order.parcelId];
          const dateDisplay = order.createdAt
            ? new Date(order.createdAt).toLocaleDateString('en-US', {
                month: 'short',
                day: 'numeric',
              })
            : '—';

          return (
            <div
              key={order.parcelId}
              className="bg-white rounded-2xl border border-slate-200/80 p-4 space-y-3.5 shadow-2xs"
            >
              {/* Card Header: Tracking & Status */}
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-1.5">
                  <span className="font-mono text-xs font-bold text-slate-900">
                    {order.trackingNumber}
                  </span>
                  <button
                    type="button"
                    onClick={() => onCopyTracking(order.trackingNumber)}
                    className="p-1 rounded text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors"
                  >
                    <Copy className="w-3 h-3" />
                  </button>
                </div>
                <OrderHistoryStatusBadge status={order.currentStatus} />
              </div>

              {/* Recipient & Route */}
              <div className="bg-slate-50/80 rounded-xl p-3 border border-slate-100 space-y-1.5 text-xs">
                <div className="flex items-center justify-between text-slate-800">
                  <span className="font-semibold">{order.recipientName}</span>
                  <span className="text-[11px] text-slate-500 font-medium">
                    {Number(order.weightKg).toFixed(1)} kg • {order.parcelSize}
                  </span>
                </div>
                <div className="flex items-center gap-1 text-[11px] text-slate-500">
                  <span>{order.originZoneName}</span>
                  <ArrowRight className="w-3 h-3 text-slate-400 shrink-0" />
                  <span className="font-medium text-slate-700">
                    {order.destinationZoneName}
                  </span>
                </div>
              </div>

              {/* Financial & Date Summary */}
              <div className="flex items-center justify-between text-xs pt-0.5">
                <div className="flex items-center gap-1 text-slate-400">
                  <Calendar className="w-3.5 h-3.5" />
                  <span>{dateDisplay}</span>
                </div>
                <div className="font-bold text-slate-900 text-sm">
                  {order.currency || 'LKR'}{' '}
                  {Number(order.estimatedFee).toLocaleString(undefined, {
                    minimumFractionDigits: 2,
                    maximumFractionDigits: 2,
                  })}
                </div>
              </div>

              {/* Card Actions */}
              <div className="grid grid-cols-2 gap-2 pt-1 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => onOpenDetail(order)}
                  className="w-full py-2 px-3 rounded-xl border border-slate-200 text-xs font-semibold text-slate-700 hover:bg-slate-50 transition-colors flex items-center justify-center gap-1.5"
                >
                  <Eye className="w-3.5 h-3.5 text-slate-500" />
                  <span>View Details</span>
                </button>

                <button
                  type="button"
                  onClick={() => onDownloadReceipt(order)}
                  disabled={isDownloading}
                  className="w-full py-2 px-3 rounded-xl bg-blue-50 border border-blue-200 text-xs font-semibold text-blue-700 hover:bg-blue-100 transition-colors flex items-center justify-center gap-1.5 disabled:opacity-50"
                >
                  {isDownloading ? (
                    <Spinner size="sm" className="text-blue-600" />
                  ) : (
                    <FileDown className="w-3.5 h-3.5" />
                  )}
                  <span>PDF Receipt</span>
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
