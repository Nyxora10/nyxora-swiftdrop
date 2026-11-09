'use client';

import React from 'react';

export default function OrderHistorySkeleton() {
  return (
    <div className="space-y-4 animate-pulse">
      {/* Desktop Shimmer Table (hidden md:block) */}
      <div className="hidden md:block bg-white rounded-2xl border border-slate-200/80 overflow-hidden shadow-2xs">
        <div className="h-10 bg-slate-50 border-b border-slate-100" />
        <div className="divide-y divide-slate-100">
          {[...Array(5)].map((_, i) => (
            <div key={i} className="flex items-center justify-between p-4 gap-4">
              <div className="w-28 h-4 bg-slate-200 rounded-md" />
              <div className="w-48 space-y-2">
                <div className="w-32 h-3.5 bg-slate-200 rounded" />
                <div className="w-44 h-2.5 bg-slate-100 rounded" />
              </div>
              <div className="w-20 h-4 bg-slate-100 rounded-md" />
              <div className="w-24 h-6 bg-slate-100 rounded-full" />
              <div className="w-20 h-4 bg-slate-200 rounded" />
              <div className="w-20 h-3 bg-slate-100 rounded" />
              <div className="w-24 h-8 bg-slate-100 rounded-lg" />
            </div>
          ))}
        </div>
      </div>

      {/* Mobile Shimmer Cards (md:hidden) */}
      <div className="md:hidden space-y-3">
        {[...Array(3)].map((_, i) => (
          <div
            key={i}
            className="bg-white rounded-2xl border border-slate-200/80 p-4 space-y-3 shadow-2xs"
          >
            <div className="flex items-center justify-between">
              <div className="w-28 h-4 bg-slate-200 rounded" />
              <div className="w-20 h-5 bg-slate-100 rounded-full" />
            </div>
            <div className="h-14 bg-slate-50 rounded-xl p-3 space-y-2">
              <div className="w-32 h-3.5 bg-slate-200 rounded" />
              <div className="w-40 h-2.5 bg-slate-100 rounded" />
            </div>
            <div className="flex items-center justify-between">
              <div className="w-16 h-3 bg-slate-100 rounded" />
              <div className="w-20 h-4 bg-slate-200 rounded" />
            </div>
            <div className="grid grid-cols-2 gap-2 pt-1 border-t border-slate-100">
              <div className="h-8 bg-slate-100 rounded-xl" />
              <div className="h-8 bg-slate-100 rounded-xl" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

