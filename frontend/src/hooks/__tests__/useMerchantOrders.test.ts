import { renderHook, waitFor, act } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '@/lib/api';
import { useMerchantOrders } from '../useMerchantOrders';

vi.mock('@/lib/api', () => ({ api: { get: vi.fn() } }));

const getMock = vi.mocked(api.get);

const history = (totalOrders: number) => ({
  data: {
    totalOrders,
    inTransitCount: 0,
    deliveredCount: 0,
    failedCount: 0,
    totalSavings: 91.5,
    orders: { content: [], pageNumber: 0, pageSize: 10, totalElements: totalOrders, totalPages: 1 },
  },
});

describe('useMerchantOrders', () => {
  beforeEach(() => getMock.mockReset());

  it('requests the page and status from the server and caps the size at 50', async () => {
    getMock.mockResolvedValue(history(3));
    const { result } = renderHook(() => useMerchantOrders({ page: 2, size: 500, status: 'DELIVERED' }));

    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(getMock).toHaveBeenCalledWith('/api/v1/parcels/merchant/orders', {
      params: { page: 2, size: 50, status: 'DELIVERED' },
    });
    expect(result.current.data?.totalOrders).toBe(3);
    expect(result.current.error).toBeNull();
  });

  it('leaves out the status parameter when no filter is set', async () => {
    getMock.mockResolvedValue(history(0));
    renderHook(() => useMerchantOrders({ page: 0, size: 10, status: null }));
    await waitFor(() => expect(getMock).toHaveBeenCalled());
    expect(getMock.mock.calls[0][1]).toEqual({ params: { page: 0, size: 10 } });
  });

  it('shows the server error and keeps the last good data', async () => {
    getMock.mockResolvedValueOnce(history(5));
    const { result } = renderHook(() => useMerchantOrders({ page: 0, size: 10 }));
    await waitFor(() => expect(result.current.data?.totalOrders).toBe(5));

    getMock.mockRejectedValueOnce({ response: { data: { detail: 'Merchant identity missing' } } });
    act(() => result.current.reload());
    expect(result.current.loading).toBe(true);

    await waitFor(() => expect(result.current.error).toBe('Merchant identity missing'));
    expect(result.current.data?.totalOrders).toBe(5);
    expect(result.current.loading).toBe(false);
  });
});
