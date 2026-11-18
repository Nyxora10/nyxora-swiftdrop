'use client';

import { useCallback, useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { apiErrorMessage } from '@/lib/merchant/format';
import type { MerchantOrderHistory, MerchantParcelStatus } from '@/types/merchant';

/** parcel-service caps the page size at 50. */
export const MAX_ORDER_PAGE_SIZE = 50;

interface Options {
  page: number;
  size: number;
  status?: MerchantParcelStatus | null;
}

interface Result {
  key: string;
  data: MerchantOrderHistory | null;
  error: string | null;
}

/**
 * Loads one page of the merchant's orders, plus lifetime counters, from
 * GET /api/v1/parcels/merchant/orders. Paging and the status filter run on the server.
 */
export function useMerchantOrders({ page, size, status }: Options) {
  const [reloadKey, setReloadKey] = useState(0);
  const [result, setResult] = useState<Result>({ key: '', data: null, error: null });

  const pageSize = Math.min(size, MAX_ORDER_PAGE_SIZE);
  const requestKey = `${page}|${pageSize}|${status ?? ''}|${reloadKey}`;

  useEffect(() => {
    let cancelled = false;
    api
      .get<MerchantOrderHistory>('/api/v1/parcels/merchant/orders', {
        params: { page, size: pageSize, ...(status ? { status } : {}) },
      })
      .then((res) => {
        if (!cancelled) setResult({ key: requestKey, data: res.data, error: null });
      })
      .catch((err) => {
        if (!cancelled)
          setResult((prev) => ({ key: requestKey, data: prev.data, error: apiErrorMessage(err, 'Could not load your orders.') }));
      });
    return () => {
      cancelled = true;
    };
  }, [page, pageSize, status, requestKey]);

  const reload = useCallback(() => setReloadKey((k) => k + 1), []);
  const loading = result.key !== requestKey;

  return {
    // The previous page stays visible while the next one loads.
    data: result.data,
    loading,
    error: loading ? null : result.error,
    reload,
  };
}
