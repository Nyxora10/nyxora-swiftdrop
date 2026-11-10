'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import {
  CheckCircle2,
  Copy,
  Check,
  Download,
  Clock,
  Package,
} from 'lucide-react';
import { BookingResponse } from '@/types/booking';
import BarcodeGraphic from './BarcodeGraphic';
import { Button } from '@/components/ui';

interface Step4ConfirmationProps {
  bookingResult: BookingResponse;
  senderName: string;
  senderAddress: string;
  recipientName: string;
  recipientAddress: string;
  originZoneName: string;
  destinationZoneName: string;
  weightKg: number;
  onDownloadPdf: () => void;
  downloadingPdf: boolean;
  onNewBooking: () => void;
}

export default function Step4Confirmation({
  bookingResult,
  senderName,
  senderAddress,
  recipientName,
  recipientAddress,
  originZoneName,
  destinationZoneName,
  weightKg,
  onDownloadPdf,
  downloadingPdf,
  onNewBooking,
}: Step4ConfirmationProps) {
  const [copied, setCopied] = useState<boolean>(false);

  const handleCopyTracking = () => {
    navigator.clipboard.writeText(bookingResult.trackingNumber);
    setCopied(true);
    setTimeout(() => setCopied(false), 2500);
  };

  return (
    <div className="space-y-6">
      {/* Celebration Header */}
      <div className="bg-emerald-600 text-white rounded-2xl p-6 sm:p-8 shadow-lg shadow-emerald-600/20 text-center space-y-2">
        <div className="w-12 h-12 bg-white/20 rounded-full flex items-center justify-center mx-auto mb-3">
          <CheckCircle2 className="w-7 h-7 text-white" />
        </div>
        <h2 className="text-xl sm:text-2xl font-black tracking-tight">
          Delivery Successfully Booked!
        </h2>
        <p className="text-xs sm:text-sm text-emerald-100 max-w-lg mx-auto">
          Your parcel has been entered into the SwiftDrop dispatch network. A courier will be assigned shortly for doorstep pickup.
        </p>
      </div>

      {/* Digital Printable Shipping Label (DHL / FedEx Style) */}
      <div className="bg-white rounded-3xl border-2 border-slate-900 p-6 sm:p-8 shadow-md max-w-2xl mx-auto space-y-6">
        {/* Label Top Bar */}
        <div className="flex items-center justify-between pb-4 border-b-2 border-slate-900">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 bg-blue-600 text-white font-black text-sm flex items-center justify-center rounded-lg">
              S
            </div>
            <div>
              <span className="font-black text-slate-900 tracking-tight text-base">SwiftDrop</span>
              <span className="text-[10px] block text-slate-500 uppercase font-bold">Express Parcel Waybill</span>
            </div>
          </div>

          <div className="text-right">
            <span className="text-[10px] font-bold uppercase tracking-wider text-slate-500 block">Service Level</span>
            <span className="text-xs font-black px-2.5 py-0.5 rounded-full bg-slate-900 text-white uppercase">
              {bookingResult.deliveryTier}
            </span>
          </div>
        </div>

        {/* Tracking Number Section */}
        <div className="text-center py-4 bg-slate-50 rounded-2xl border border-slate-200">
          <span className="text-[10px] uppercase font-extrabold text-slate-400 tracking-wider">
            Linear Consignment Tracking Number
          </span>
          <div className="flex items-center justify-center gap-3 mt-1">
            <span className="text-xl sm:text-2xl font-mono font-black text-slate-900 tracking-wider">
              {bookingResult.trackingNumber}
            </span>
            <button
              type="button"
              onClick={handleCopyTracking}
              className="p-1.5 rounded-lg text-slate-500 hover:bg-slate-200 transition-all cursor-pointer"
              title="Copy Tracking Number"
              aria-label="Copy Tracking Number"
            >
              {copied ? <Check className="w-4 h-4 text-emerald-600" /> : <Copy className="w-4 h-4" />}
            </button>
          </div>

          {/* Reusable Code-128 Linear Barcode Component */}
          <div className="mt-4">
            <BarcodeGraphic code={bookingResult.trackingNumber} height={52} />
          </div>
        </div>

        {/* Route & Contact Summary */}
        <div className="grid grid-cols-2 gap-4 text-xs pt-2">
          <div className="p-3 bg-slate-50/60 rounded-xl border border-slate-100">
            <span className="text-[10px] font-bold uppercase text-slate-400 block mb-1">From (Origin)</span>
            <p className="font-bold text-slate-900">{senderName}</p>
            <p className="text-slate-600 text-[11px] mt-0.5">{senderAddress}</p>
            <p className="text-[11px] font-semibold text-blue-700 mt-1">{originZoneName}</p>
          </div>

          <div className="p-3 bg-slate-50/60 rounded-xl border border-slate-100">
            <span className="text-[10px] font-bold uppercase text-slate-400 block mb-1">To (Destination)</span>
            <p className="font-bold text-slate-900">{recipientName}</p>
            <p className="text-slate-600 text-[11px] mt-0.5">{recipientAddress}</p>
            <p className="text-[11px] font-semibold text-emerald-700 mt-1">{destinationZoneName}</p>
          </div>
        </div>

        {/* Bottom Specs & Status */}
        <div className="flex items-center justify-between pt-3 border-t border-slate-200 text-xs">
          <div>
            <span className="text-slate-400 text-[10px] block">Total Amount</span>
            <span className="font-black text-slate-900 text-sm">
              LKR {Number(bookingResult.totalChargeLkr).toFixed(2)}
            </span>
          </div>
          <div>
            <span className="text-slate-400 text-[10px] block">Status</span>
            <span className="font-bold text-blue-700 bg-blue-50 px-2 py-0.5 rounded-full border border-blue-200 text-[11px]">
              {bookingResult.status}
            </span>
          </div>
          <div>
            <span className="text-slate-400 text-[10px] block">Weight</span>
            <span className="font-bold text-slate-900 text-[11px]">{weightKg.toFixed(1)} kg</span>
          </div>
        </div>
      </div>

      {/* Post-Booking Action Buttons */}
      <div className="flex flex-col sm:flex-row justify-center items-center gap-3 pt-4">
        <Button
          type="button"
          onClick={onDownloadPdf}
          loading={downloadingPdf}
          leftIcon={<Download className="w-4 h-4" />}
          className="w-full sm:w-auto shadow-md shadow-blue-500/20"
        >
          Download Official PDF Receipt
        </Button>

        <Link
          href="/customer/history"
          className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-xl text-xs font-semibold bg-white border border-slate-200 text-slate-700 hover:bg-slate-50 transition-all shadow-2xs"
        >
          <Clock className="w-3.5 h-3.5 text-slate-500" />
          <span>Go to Shipment History</span>
        </Link>

        <Button
          type="button"
          variant="secondary"
          onClick={onNewBooking}
          leftIcon={<Package className="w-3.5 h-3.5 text-slate-500" />}
          className="w-full sm:w-auto bg-slate-100 border-transparent hover:bg-slate-200"
        >
          Book Another Parcel
        </Button>
      </div>
    </div>
  );
}
