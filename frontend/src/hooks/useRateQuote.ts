'use client';

import { useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import { BookingFormData, RateQuoteResponse } from '@/types/booking';
import { getRateQuote } from '@/services/parcelService';

export function useRateQuote(formData: BookingFormData, currentStep: number) {
  const [quoteResult, setQuoteResult] = useState<RateQuoteResponse | null>(null);
  const [quoteLoading, setQuoteLoading] = useState<boolean>(false);
  const [quoteError, setQuoteError] = useState<string | null>(null);

  const fetchQuote = useCallback(async () => {
    // Only fetch if origin and destination are selected
    if (!formData.originZoneId || !formData.destinationZoneId) {
      setQuoteResult(null);
      setQuoteError('Origin and destination delivery zones must be selected to calculate a rate quote.');
      return;
    }

    setQuoteLoading(true);
    setQuoteError(null);
    try {
      const quote = await getRateQuote(formData);
      setQuoteResult(quote);
    } catch (error: unknown) {
      console.error('API quote request error:', error);
      const errorMsg = axios.isAxiosError(error)
        ? (error.response?.data?.message as string | undefined) ||
          (error.response?.data?.detail as string | undefined) ||
          'Unable to calculate real-time delivery quote from dispatch engine.'
        : 'Unable to calculate real-time delivery quote. Please check your network and try again.';
      setQuoteResult(null);
      setQuoteError(errorMsg);
    } finally {
      setQuoteLoading(false);
    }
  }, [formData]);

  // Trigger quote calculation whenever entering Step 3 or when quote parameters update
  useEffect(() => {
    if (currentStep !== 3) return;

    const timeoutId = window.setTimeout(() => {
      void fetchQuote();
    }, 0);
    return () => window.clearTimeout(timeoutId);
  }, [currentStep, fetchQuote]);

  const clearQuote = useCallback(() => {
    setQuoteResult(null);
    setQuoteError(null);
  }, []);

  return {
    quoteResult,
    quoteLoading,
    quoteError,
    fetchQuote,
    clearQuote,
  };
}
