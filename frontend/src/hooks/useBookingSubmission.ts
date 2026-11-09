'use client';

import { useState, useCallback } from 'react';
import axios from 'axios';
import { authStorage } from '@/lib/auth';
import { BookingFormData, BookingResponse, DeliveryZone, RateQuoteResponse } from '@/types/booking';
import { bookParcel } from '@/services/parcelService';

export interface UseBookingSubmissionOptions {
  formData: BookingFormData;
  quoteResult: RateQuoteResponse | null;
  originZone?: DeliveryZone;
  destZone?: DeliveryZone;
  onSuccess: () => void;
  toast: {
    success: (msg: string) => void;
    error: (msg: string) => void;
  };
}

export function useBookingSubmission({
  formData,
  quoteResult,
  originZone,
  destZone,
  onSuccess,
  toast,
}: UseBookingSubmissionOptions) {
  const [bookingLoading, setBookingLoading] = useState<boolean>(false);
  const [bookingResult, setBookingResult] = useState<BookingResponse | null>(null);

  const confirmBooking = useCallback(async () => {
    setBookingLoading(true);
    const token = authStorage.getAccessToken();
    const isMock = !token || token.startsWith('mock-');

    if (isMock) {
      // Seamless mock simulation for offline testing
      setTimeout(() => {
        const dateStr = new Date().toISOString().slice(0, 10).replace(/-/g, '');
        const randomCode = Math.random().toString(36).substring(2, 8).toUpperCase();
        const demoResponse: BookingResponse = {
          parcelId: '00000000-0000-0000-0000-000000000001',
          trackingNumber: `SD-${dateStr}-${randomCode}`,
          status: 'CREATED',
          originZoneName: originZone?.zoneName || 'Colombo Central Metro',
          destinationZoneName: destZone?.zoneName || 'Gampaha Industrial Hub',
          totalChargeLkr: quoteResult?.totalEstimatedFee || 990.0,
          deliveryTier: formData.deliveryTier,
          createdAt: new Date().toISOString(),
        };
        setBookingResult(demoResponse);
        onSuccess();
        toast.success(`Booking Confirmed! Tracking: ${demoResponse.trackingNumber}`);
        setBookingLoading(false);
      }, 600);
      return;
    }

    try {
      const response = await bookParcel(formData);

      // Enhance with resolved zone names and fallback fees if needed
      const enrichedResult: BookingResponse = {
        ...response,
        originZoneName: response.originZoneName || originZone?.zoneName || 'Origin Zone',
        destinationZoneName: response.destinationZoneName || destZone?.zoneName || 'Destination Zone',
        totalChargeLkr: response.totalChargeLkr || quoteResult?.totalEstimatedFee || 0,
      };

      setBookingResult(enrichedResult);
      onSuccess();
      toast.success(`Booking Confirmed! Tracking: ${enrichedResult.trackingNumber}`);
    } catch (error: unknown) {
      console.error('Booking submission failed:', error);
      const errorData = axios.isAxiosError(error)
        ? error.response?.data as {
            errors?: Record<string, unknown>;
            detail?: string;
            message?: string;
          } | undefined
        : undefined;
      const fieldErrors = errorData?.errors
        ? Object.entries(errorData.errors)
            .map(([field, message]) => `${field}: ${String(message)}`)
            .join(', ')
        : null;

      const errorMsg =
        fieldErrors ||
        errorData?.detail ||
        errorData?.message ||
        'Booking submission failed. Please verify parcel details and try again.';

      toast.error(errorMsg);
    } finally {
      setBookingLoading(false);
    }
  }, [formData, quoteResult, originZone, destZone, onSuccess, toast]);

  const resetBookingResult = useCallback(() => {
    setBookingResult(null);
  }, []);

  return {
    bookingResult,
    bookingLoading,
    confirmBooking,
    resetBookingResult,
  };
}

