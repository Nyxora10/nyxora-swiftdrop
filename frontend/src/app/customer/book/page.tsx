'use client';

import React from 'react';
import Link from 'next/link';
import { Package, Clock } from 'lucide-react';
import AppShell from '@/components/layout/AppShell';
import { useUserSession } from '@/lib/auth';
import { useToast } from '@/components/layout/ToastContext';

// Hooks & Services
import { useDeliveryZones } from '@/hooks/useDeliveryZones';
import { useBookingWizard } from '@/hooks/useBookingWizard';
import { useRateQuote } from '@/hooks/useRateQuote';
import { useBookingSubmission } from '@/hooks/useBookingSubmission';
import { usePdfReceipt } from '@/hooks/usePdfReceipt';

// Feature Components
import BookingStepper from '@/components/customer/booking/BookingStepper';
import Step1RouteContacts from '@/components/customer/booking/Step1RouteContacts';
import Step2ParcelSpecs from '@/components/customer/booking/Step2ParcelSpecs';
import Step3RateQuote from '@/components/customer/booking/Step3RateQuote';
import Step4Confirmation from '@/components/customer/booking/Step4Confirmation';

export default function CustomerBookingPage() {
  const user = useUserSession();
  const toast = useToast();

  // 1. Data Layer: Logistics Zones
  const { zones, loadingZones } = useDeliveryZones();

  // 2. Wizard & Form State
  const {
    currentStep,
    formData,
    updateFormData,
    nextStep,
    prevStep,
    goToStep,
    resetForm,
  } = useBookingWizard({
    userFullName: user?.fullName,
    zones,
  });

  // 3. Dynamic Rate Quoting
  const { quoteResult, quoteLoading, quoteError, fetchQuote, clearQuote } = useRateQuote(
    formData,
    currentStep
  );

  const originZone = zones.find((z) => z.id === formData.originZoneId);
  const destZone = zones.find((z) => z.id === formData.destinationZoneId);

  // 4. Booking Submission & Orchestration
  const {
    bookingResult,
    bookingLoading,
    confirmBooking,
    resetBookingResult,
  } = useBookingSubmission({
    formData,
    quoteResult,
    originZone,
    destZone,
    onSuccess: () => goToStep(4),
    toast,
  });

  // 5. Digital PDF Receipt Generation
  const { downloadingPdf, downloadPdf } = usePdfReceipt({ toast });

  const handleResetForNewBooking = () => {
    resetForm();
    clearQuote();
    resetBookingResult();
  };

  return (
    <AppShell>
      <div className="max-w-5xl mx-auto space-y-8 pb-12">
        {/* Page Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-blue-600 mb-1">
              <Package className="w-4 h-4" />
              <span>Customer Self-Service Delivery</span>
            </div>
            <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">
              Book a Parcel Delivery
            </h1>
            <p className="text-sm text-slate-500 mt-1">
              Dispatch shipments island-wide across Sri Lanka with real-time rate estimation and linear barcode tracking.
            </p>
          </div>

          <Link
            href="/customer/history"
            className="inline-flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold bg-white border border-slate-200 text-slate-700 hover:bg-slate-50 shadow-2xs transition-all self-start sm:self-auto"
          >
            <Clock className="w-3.5 h-3.5 text-slate-500" />
            <span>My Shipment History</span>
          </Link>
        </div>

        {/* 4-Step Stepper Component */}
        <BookingStepper currentStep={currentStep} />

        {/* Step 1: Route & Contacts */}
        {currentStep === 1 && (
          <Step1RouteContacts
            formData={formData}
            onChange={updateFormData}
            zones={zones}
            loadingZones={loadingZones}
            onNext={nextStep}
            isLoggedIn={Boolean(user)}
          />
        )}

        {/* Step 2: Parcel Dimensions & Service Tier */}
        {currentStep === 2 && (
          <Step2ParcelSpecs
            formData={formData}
            onChange={updateFormData}
            onBack={prevStep}
            onNext={nextStep}
          />
        )}

        {/* Step 3: Dynamic Rate Quote */}
        {currentStep === 3 && (
          <Step3RateQuote
            formData={formData}
            quote={quoteResult}
            loading={quoteLoading}
            error={quoteError}
            zones={zones}
            onRecalculate={fetchQuote}
            onBack={prevStep}
            onConfirm={confirmBooking}
            bookingLoading={bookingLoading}
            onEditSpecs={() => goToStep(2)}
          />
        )}

        {/* Step 4: Confirmation & Shipping Label */}
        {currentStep === 4 && bookingResult && (
          <Step4Confirmation
            bookingResult={bookingResult}
            senderName={formData.senderName}
            senderAddress={formData.senderAddress}
            recipientName={formData.recipientName}
            recipientAddress={formData.recipientAddress}
            originZoneName={originZone?.zoneName || bookingResult.originZoneName}
            destinationZoneName={destZone?.zoneName || bookingResult.destinationZoneName}
            weightKg={formData.weightKg}
            onDownloadPdf={() => downloadPdf(bookingResult)}
            downloadingPdf={downloadingPdf}
            onNewBooking={handleResetForNewBooking}
          />
        )}
      </div>
    </AppShell>
  );
}
