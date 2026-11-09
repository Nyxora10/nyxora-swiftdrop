'use client';

import React from 'react';
import AppShell from '@/components/layout/AppShell';
import { useCustomerOrders } from '@/hooks/useCustomerOrders';
import {
  OrderHistoryHeader,
  OrderHistoryFilters,
  OrderHistoryTable,
  OrderDetailModal,
  OrderHistoryPagination,
  OrderHistorySkeleton,
  OrderHistoryEmptyState,
} from '@/components/customer/history';

export default function CustomerHistoryPage() {
  const {
    orders,
    page,
    pageSize,
    totalElements,
    totalPages,
    isFirst,
    isLast,
    statusFilter,
    searchQuery,
    debouncedSearch,
    loading,
    receiptLoadingMap,
    selectedOrder,
    setPage,
    setPageSize,
    setStatusFilter,
    setSearchQuery,
    clearSearch,
    refetchOrders,
    copyTrackingNumber,
    downloadReceipt,
    openOrderDetail,
    closeOrderDetail,
  } = useCustomerOrders();

  const isFiltered = statusFilter !== 'ALL' || !!debouncedSearch.trim();

  const handleClearFilters = () => {
    setStatusFilter('ALL');
    clearSearch();
  };

  return (
    <AppShell>
      <div className="space-y-6 max-w-7xl mx-auto pb-12">
        {/* Page Title & Quick Actions */}
        <OrderHistoryHeader
          totalElements={totalElements}
          loading={loading}
          onRefresh={refetchOrders}
        />

        {/* Tab & Search Controls */}
        <OrderHistoryFilters
          statusFilter={statusFilter}
          onStatusChange={setStatusFilter}
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          onClearSearch={clearSearch}
          pageSize={pageSize}
          onPageSizeChange={setPageSize}
          totalFiltered={orders.length}
        />

        {/* Main Content Area */}
        {loading ? (
          <OrderHistorySkeleton />
        ) : orders.length === 0 ? (
          <OrderHistoryEmptyState
            isFiltered={isFiltered}
            onClearFilters={handleClearFilters}
          />
        ) : (
          <div className="space-y-4">
            <OrderHistoryTable
              orders={orders}
              onCopyTracking={copyTrackingNumber}
              onOpenDetail={openOrderDetail}
              onDownloadReceipt={downloadReceipt}
              receiptLoadingMap={receiptLoadingMap}
            />

            <OrderHistoryPagination
              page={page}
              pageSize={pageSize}
              totalElements={totalElements}
              totalPages={totalPages}
              isFirst={isFirst}
              isLast={isLast}
              onPageChange={setPage}
            />
          </div>
        )}

        {/* Order Detail Slide-Over Modal */}
        <OrderDetailModal
          order={selectedOrder}
          onClose={closeOrderDetail}
          onCopyTracking={copyTrackingNumber}
          onDownloadReceipt={downloadReceipt}
          isDownloadingReceipt={
            !!selectedOrder && !!receiptLoadingMap[selectedOrder.parcelId]
          }
        />
      </div>
    </AppShell>
  );
}
