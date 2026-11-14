'use client';

import { useCallback, useEffect, useState } from 'react';
import axios from 'axios';
import { getParcelTracking } from '@/services/parcelService';
import { ParcelTrackingResponse } from '@/types/tracking';

function getTrackingErrorMessage(error: unknown): string {
  if (!axios.isAxiosError(error)) {
    return 'Unable to load parcel tracking information. Please try again.';
  }

  if (!error.response) {
    return 'Unable to reach the tracking service. Please check your network connection and try again.';
  }

  if (error.response.status === 404) {
    return 'No parcel was found for this tracking number.';
  }

  const responseData = error.response.data as
    | { message?: string; detail?: string }
    | undefined;
  return (
    responseData?.message ||
    responseData?.detail ||
    'Unable to load parcel tracking information. Please try again.'
  );
}

export function useParcelTracking(trackingNumber: string) {
  const [tracking, setTracking] = useState<ParcelTrackingResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchTracking = useCallback(async () => {
    const normalizedTrackingNumber = trackingNumber.trim();

    if (!normalizedTrackingNumber) {
      setTracking(null);
      setError('A tracking number is required.');
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const response = await getParcelTracking(normalizedTrackingNumber);
      setTracking({
        ...response,
        timeline: response.timeline ?? [],
      });
    } catch (requestError: unknown) {
      console.error('Failed to retrieve parcel tracking information:', requestError);
      setTracking(null);
      setError(getTrackingErrorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }, [trackingNumber]);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      void fetchTracking();
    }, 0);

    return () => window.clearTimeout(timeoutId);
  }, [fetchTracking]);

  return {
    tracking,
    loading,
    error,
    hasTimeline: Boolean(tracking?.timeline.length),
    refetchTracking: fetchTracking,
  };
}
