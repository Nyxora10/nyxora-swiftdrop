'use client';

import { useState, useEffect, useCallback } from 'react';
import { DeliveryZone } from '@/types/booking';
import { getDeliveryZones } from '@/services/zoneService';

export function useDeliveryZones() {
  const [zones, setZones] = useState<DeliveryZone[]>([]);
  const [loadingZones, setLoadingZones] = useState<boolean>(true);
  const [zoneError, setZoneError] = useState<string | null>(null);

  const fetchZones = useCallback(async () => {
    setLoadingZones(true);
    setZoneError(null);
    try {
      const data = await getDeliveryZones();
      setZones(data);
    } catch (err: unknown) {
      console.error('Failed to retrieve delivery zones from logistics service:', err);
      setZoneError('Unable to load active delivery zones. Please verify network connectivity.');
    } finally {
      setLoadingZones(false);
    }
  }, []);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      void fetchZones();
    }, 0);

    return () => window.clearTimeout(timeoutId);
  }, [fetchZones]);

  return { zones, loadingZones, zoneError, refetchZones: fetchZones };
}
