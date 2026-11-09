'use client';

import { useState, useEffect } from 'react';
import { DeliveryZone } from '@/types/booking';
import { DEFAULT_FALLBACK_ZONES } from '@/constants/booking';
import { getDeliveryZones } from '@/services/zoneService';

export function useDeliveryZones() {
  const [zones, setZones] = useState<DeliveryZone[]>(DEFAULT_FALLBACK_ZONES);
  const [loadingZones, setLoadingZones] = useState<boolean>(true);

  useEffect(() => {
    let isMounted = true;

    async function load() {
      try {
        setLoadingZones(true);
        const data = await getDeliveryZones();
        if (isMounted) {
          setZones(data);
        }
      } catch (err) {
        console.warn('Fallback zones in useDeliveryZones:', err);
      } finally {
        if (isMounted) {
          setLoadingZones(false);
        }
      }
    }

    load();

    return () => {
      isMounted = false;
    };
  }, []);

  return { zones, loadingZones };
}

