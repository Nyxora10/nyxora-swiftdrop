'use client';

import { useState, useEffect, useCallback } from 'react';
import { BookingFormData, RateQuoteResponse } from '@/types/booking';
import { calculateFallbackQuote } from '@/constants/booking';
import { getRateQuote } from '@/services/parcelService';

export function useRateQuote(formData: BookingFormData, currentStep: number) {
  const [quoteResult, setQuoteResult] = useState<RateQuoteResponse | null>(null);
  const [quoteLoading, setQuoteLoading] = useState<boolean>(false);
  const [quoteError, setQuoteError] = useState<string | null>(null);

  const fetchQuote = useCallback(async () => {
    setQuoteLoading(true);
    setQuoteError(null);
    try {
      const quote = await getRateQuote(formData);
      setQuoteResult(quote);
    } catch (err: any) {
      console.warn('API quote request error, computing fallback estimate:', err);
      // Fallback calculation for offline resilience
      const fallback = calculateFallbackQuote(formData);
      setQuoteResult(fallback);
    } finally {
      setQuoteLoading(false);
    }
  }, [
    formData.originZoneId,
    formData.destinationZoneId,
    formData.weightKg,
    formData.parcelSize,
    formData.deliveryTier,
  ]);

  // Trigger quote calculation whenever entering Step 3 or when quote parameters update
  useEffect(() => {
    if (currentStep === 3) {
      fetchQuote();
    }
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

