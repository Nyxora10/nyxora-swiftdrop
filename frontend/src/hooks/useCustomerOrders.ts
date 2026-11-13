'use client';

import { useState, useEffect, useCallback, useMemo } from 'react';
import {
  CustomerOrderQuery,
  OrderStatusFilter,
  OrderSummary,
  PagedOrderResponse,
  ParcelStatus,
} from '@/types/order';
import { getCustomerOrders, downloadReceiptPdf } from '@/services/parcelService';
import { useToast } from '@/components/layout/ToastContext';

/**
 * Maps UI status filter tabs to backend ParcelStatus values.
 */
function mapFilterToBackendStatus(filter: OrderStatusFilter): ParcelStatus | undefined {
  switch (filter) {
    case 'IN_TRANSIT':
      return 'IN_TRANSIT';
    case 'OUT_FOR_DELIVERY':
      return 'OUT_FOR_DELIVERY';
    case 'DELIVERED':
      return 'DELIVERED';
    case 'CREATED':
      return 'CREATED';
    case 'FAILED_OR_RETURNED':
      return 'FAILED';
    case 'ALL':
    default:
      return undefined;
  }
}

export function useCustomerOrders() {
  const toast = useToast();

  // Pagination & Filtering State
  const [page, setPage] = useState<number>(0);
  const [pageSize, setPageSize] = useState<number>(10);
  const [statusFilter, setStatusFilter] = useState<OrderStatusFilter>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [debouncedSearch, setDebouncedSearch] = useState<string>('');

  // Data & Lifecycle State
  const [pagedData, setPagedData] = useState<PagedOrderResponse<OrderSummary>>({
    content: [],
    pageNumber: 0,
    pageSize: 10,
    totalElements: 0,
    totalPages: 0,
    isFirst: true,
    isLast: true,
  });
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Per-order receipt download loading tracker
  const [receiptLoadingMap, setReceiptLoadingMap] = useState<Record<string, boolean>>({});

  // Detail Modal inspection state
  const [selectedOrder, setSelectedOrder] = useState<OrderSummary | null>(null);

  // Debounce search query input (350ms)
  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedSearch(searchQuery);
    }, 350);
    return () => clearTimeout(handler);
  }, [searchQuery]);

  // Reset page when filter or search changes
  const handleStatusFilterChange = useCallback((newFilter: OrderStatusFilter) => {
    setStatusFilter(newFilter);
    setPage(0);
  }, []);

  const handlePageSizeChange = useCallback((newSize: number) => {
    setPageSize(newSize);
    setPage(0);
  }, []);

  const handleSearchChange = useCallback((text: string) => {
    setSearchQuery(text);
    setPage(0);
  }, []);

  const clearSearch = useCallback(() => {
    setSearchQuery('');
    setDebouncedSearch('');
    setPage(0);
  }, []);

  // Fetch orders from parcel service
  const fetchOrders = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const query: CustomerOrderQuery = {
        page,
        size: pageSize,
        status: mapFilterToBackendStatus(statusFilter),
      };

      const result = await getCustomerOrders(query);
      setPagedData(result);
    } catch (err: unknown) {
      const errMsg =
        err instanceof Error ? err.message : 'Unable to retrieve shipment history. Please try again.';
      setError(errMsg);
      toast.error(errMsg);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, statusFilter, toast]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchOrders();
  }, [fetchOrders]);

  // Client-side text filter over currently loaded page (tracking #, recipient, dropoff address, destination zone)
  const filteredOrders = useMemo(() => {
    if (!debouncedSearch.trim()) {
      return pagedData.content;
    }
    const q = debouncedSearch.toLowerCase().trim();
    return pagedData.content.filter((order) => {
      const matchesTracking = order.trackingNumber?.toLowerCase().includes(q);
      const matchesRecipient = order.recipientName?.toLowerCase().includes(q);
      const matchesDestZone = order.destinationZoneName?.toLowerCase().includes(q);
      const matchesAddress = order.dropoffAddress?.toLowerCase().includes(q);
      return matchesTracking || matchesRecipient || matchesDestZone || matchesAddress;
    });
  }, [pagedData.content, debouncedSearch]);

  // Copy tracking number to clipboard with feedback toast
  const copyTrackingNumber = useCallback(
    async (trackingNumber: string) => {
      if (!trackingNumber) return;
      try {
        await navigator.clipboard.writeText(trackingNumber);
        toast.success(`Copied tracking number ${trackingNumber} to clipboard`);
      } catch {
        toast.error('Failed to copy tracking number');
      }
    },
    [toast]
  );

  // Download official PDF receipt
  const downloadReceipt = useCallback(
    async (order: OrderSummary) => {
      const parcelId = order.parcelId;
      if (!parcelId || receiptLoadingMap[parcelId]) return;

      setReceiptLoadingMap((prev) => ({ ...prev, [parcelId]: true }));
      try {
        const blob = await downloadReceiptPdf(parcelId);
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `SwiftDrop_Receipt_${order.trackingNumber || parcelId}.pdf`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);

        toast.success(`Receipt for ${order.trackingNumber} downloaded successfully.`);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : 'Failed to download receipt PDF.';
        toast.error(msg);
      } finally {
        setReceiptLoadingMap((prev) => ({ ...prev, [parcelId]: false }));
      }
    },
    [receiptLoadingMap, toast]
  );

  return {
    orders: filteredOrders,
    rawOrders: pagedData.content,
    page,
    pageSize,
    totalElements: pagedData.totalElements,
    totalPages: pagedData.totalPages,
    isFirst: pagedData.isFirst,
    isLast: pagedData.isLast,
    statusFilter,
    searchQuery,
    debouncedSearch,
    loading,
    error,
    receiptLoadingMap,
    selectedOrder,
    setPage,
    setPageSize: handlePageSizeChange,
    setStatusFilter: handleStatusFilterChange,
    setSearchQuery: handleSearchChange,
    clearSearch,
    refetchOrders: fetchOrders,
    copyTrackingNumber,
    downloadReceipt,
    openOrderDetail: setSelectedOrder,
    closeOrderDetail: () => setSelectedOrder(null),
  };
}
