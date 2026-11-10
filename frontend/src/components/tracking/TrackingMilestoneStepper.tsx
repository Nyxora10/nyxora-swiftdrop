'use client';

import { Check, Circle } from 'lucide-react';
import type { ParcelStatus } from '@/types/tracking';

const milestones: Array<{ status: ParcelStatus; label: string }> = [
  { status: 'CREATED', label: 'Created' },
  { status: 'PICKED_UP', label: 'Picked up' },
  { status: 'IN_TRANSIT', label: 'In transit' },
  { status: 'OUT_FOR_DELIVERY', label: 'Out for delivery' },
  { status: 'DELIVERED', label: 'Delivered' },
];

const milestoneIndexes = new Map(milestones.map((milestone, index) => [milestone.status, index]));

function getCurrentIndex(status: ParcelStatus): number {
  if (status === 'ASSIGNED') {
    return 0;
  }

  if (status === 'FAILED' || status === 'RETURNED_TO_SENDER') {
    return status === 'RETURNED_TO_SENDER' ? milestones.length - 1 : 3;
  }

  return milestoneIndexes.get(status) ?? 0;
}

interface TrackingMilestoneStepperProps {
  currentStatus: ParcelStatus;
  className?: string;
}

export default function TrackingMilestoneStepper({
  currentStatus,
  className = '',
}: TrackingMilestoneStepperProps) {
  const currentIndex = getCurrentIndex(currentStatus);
  const isFailed = currentStatus === 'FAILED';
  const isReturned = currentStatus === 'RETURNED_TO_SENDER';

  return (
    <div
      className={`w-full rounded-2xl border border-slate-200/80 bg-white p-5 shadow-2xs sm:p-6 ${className}`}
      aria-label={`Parcel progress: ${currentStatus}`}
    >
      <div className="flex items-start">
        {milestones.map((milestone, index) => {
          const isCompleted = index < currentIndex;
          const isCurrent = index === currentIndex;
          const isTerminalIssue = isCurrent && (isFailed || isReturned);

          return (
            <div key={milestone.status} className="flex min-w-0 flex-1 items-start">
              <div className="flex min-w-0 flex-1 flex-col items-center">
                <div
                  className={`relative z-10 flex h-9 w-9 items-center justify-center rounded-full border-2 transition-all duration-500 ${
                    isTerminalIssue
                      ? 'border-rose-500 bg-rose-50 text-rose-600'
                      : isCompleted || isCurrent
                        ? 'border-blue-600 bg-blue-600 text-white shadow-sm shadow-blue-500/25'
                        : 'border-slate-200 bg-white text-slate-400'
                  } ${isCurrent ? 'scale-110' : 'scale-100'}`}
                >
                  {isCompleted ? (
                    <Check className="h-4 w-4" aria-hidden="true" />
                  ) : isCurrent ? (
                    <span className="h-2.5 w-2.5 rounded-full bg-current" aria-hidden="true" />
                  ) : (
                    <Circle className="h-3.5 w-3.5" aria-hidden="true" />
                  )}
                </div>
                <span
                  className={`mt-2 text-center text-[10px] font-semibold leading-tight transition-colors duration-500 sm:text-xs ${
                    isCurrent || isCompleted ? 'text-slate-900' : 'text-slate-400'
                  }`}
                >
                  {milestone.label}
                </span>
              </div>

              {index < milestones.length - 1 && (
                <div
                  className={`mt-4 h-0.5 flex-1 transition-colors duration-500 ${
                    index < currentIndex ? 'bg-blue-600' : 'bg-slate-200'
                  }`}
                  aria-hidden="true"
                />
              )}
            </div>
          );
        })}
      </div>

      {(isFailed || isReturned) && (
        <p className="mt-4 text-center text-xs font-semibold text-rose-600">
          {isFailed ? 'Delivery attempt failed' : 'Returned to sender'}
        </p>
      )}
    </div>
  );
}
