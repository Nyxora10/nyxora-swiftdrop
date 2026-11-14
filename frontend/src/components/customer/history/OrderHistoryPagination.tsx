'use client';

import React from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';

interface OrderHistoryPaginationProps {
  page: number; // 0-indexed
  pageSize: number;
  totalElements: number;
  totalPages: number;
  isFirst: boolean;
  isLast: boolean;
  onPageChange: (newPage: number) => void;
}

export default function OrderHistoryPagination({
  page,
  pageSize,
  totalElements,
  totalPages,
  isFirst,
  isLast,
  onPageChange,
}: OrderHistoryPaginationProps) {
  if (totalElements === 0 || totalPages <= 1) {
    return null;
  }

  const startItem = page * pageSize + 1;
  const endItem = Math.min((page + 1) * pageSize, totalElements);

  // Generate numbered pages array with bounds (max 5 buttons visible)
  const renderPageButtons = () => {
    const pages: number[] = [];
    const maxVisible = 5;
    let startPage = Math.max(0, page - Math.floor(maxVisible / 2));
    const endPage = Math.min(totalPages - 1, startPage + maxVisible - 1);

    if (endPage - startPage + 1 < maxVisible) {
      startPage = Math.max(0, endPage - maxVisible + 1);
    }

    for (let i = startPage; i <= endPage; i++) {
      pages.push(i);
    }

    return pages.map((p) => {
      const isCurrent = p === page;
      return (
        <button
          key={p}
          type="button"
          onClick={() => onPageChange(p)}
          className={`w-8 h-8 rounded-lg text-xs font-semibold transition-all cursor-pointer ${
            isCurrent
              ? 'bg-blue-600 text-white shadow-2xs'
              : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
          }`}
          aria-current={isCurrent ? 'page' : undefined}
        >
          {p + 1}
        </button>
      );
    });
  };

  return (
    <div className="flex flex-col sm:flex-row items-center justify-between gap-3 pt-4 border-t border-slate-200/80 text-xs text-slate-500">
      <div>
        Showing <span className="font-semibold text-slate-800">{startItem}</span> to{' '}
        <span className="font-semibold text-slate-800">{endItem}</span> of{' '}
        <span className="font-semibold text-slate-800">{totalElements}</span> shipments
      </div>

      <div className="flex items-center gap-1">
        <button
          type="button"
          onClick={() => onPageChange(page - 1)}
          disabled={isFirst}
          className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg border border-slate-200 text-slate-600 hover:text-slate-900 hover:bg-slate-50 transition-colors disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
        >
          <ChevronLeft className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">Previous</span>
        </button>

        <div className="flex items-center gap-1 mx-1">{renderPageButtons()}</div>

        <button
          type="button"
          onClick={() => onPageChange(page + 1)}
          disabled={isLast}
          className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg border border-slate-200 text-slate-600 hover:text-slate-900 hover:bg-slate-50 transition-colors disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
        >
          <span className="hidden sm:inline">Next</span>
          <ChevronRight className="w-3.5 h-3.5" />
        </button>
      </div>
    </div>
  );
}
