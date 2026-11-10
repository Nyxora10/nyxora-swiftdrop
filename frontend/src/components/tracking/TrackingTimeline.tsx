import { MapPin, MessageSquareText } from 'lucide-react';
import type { TrackingTimelineEvent } from '@/types/tracking';

interface TrackingTimelineEntry extends TrackingTimelineEvent {
  zoneCode?: string | null;
}

interface TrackingTimelineProps {
  entries: TrackingTimelineEntry[];
  className?: string;
}

function formatTimestamp(timestamp: string): string {
  const date = new Date(timestamp);
  if (Number.isNaN(date.getTime())) {
    return timestamp;
  }

  return new Intl.DateTimeFormat('en', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}

function formatStatus(status: TrackingTimelineEvent['status']): string {
  return status
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

export default function TrackingTimeline({
  entries,
  className = '',
}: TrackingTimelineProps) {
  if (entries.length === 0) {
    return (
      <div className={`rounded-2xl border border-slate-200/80 bg-white p-6 shadow-2xs ${className}`}>
        <h2 className="text-sm font-bold text-slate-900">Delivery timeline</h2>
        <p className="mt-3 text-xs text-slate-500">No delivery milestones are available yet.</p>
      </div>
    );
  }

  return (
    <section
      className={`rounded-2xl border border-slate-200/80 bg-white p-6 shadow-2xs ${className}`}
      aria-labelledby="tracking-timeline-heading"
    >
      <div className="mb-5 border-b border-slate-100 pb-4">
        <h2 id="tracking-timeline-heading" className="text-sm font-bold text-slate-900">
          Delivery timeline
        </h2>
        <p className="mt-0.5 text-xs text-slate-500">Chronological parcel status updates</p>
      </div>

      <ol className="space-y-0">
        {entries.map((entry, index) => (
          <li key={`${entry.status}-${entry.timestamp}-${index}`} className="relative flex gap-3 pb-6 last:pb-0">
            {index < entries.length - 1 && (
              <span
                className="absolute left-[7px] top-4 h-full w-px bg-slate-200"
                aria-hidden="true"
              />
            )}
            <span className="relative mt-1 h-4 w-4 shrink-0 rounded-full border-4 border-blue-100 bg-blue-600" />
            <div className="min-w-0 flex-1">
              <div className="flex flex-col justify-between gap-1 sm:flex-row sm:items-center">
                <h3 className="text-xs font-bold text-slate-900">{formatStatus(entry.status)}</h3>
                <time className="text-[11px] text-slate-400" dateTime={entry.timestamp}>
                  {formatTimestamp(entry.timestamp)}
                </time>
              </div>
              {entry.zoneCode && (
                <p className="mt-1 inline-flex items-center gap-1 text-[11px] font-semibold text-blue-600">
                  <MapPin className="h-3 w-3" aria-hidden="true" />
                  {entry.zoneCode}
                </p>
              )}
              {entry.deliveryNote && (
                <p className="mt-2 flex items-start gap-1.5 text-xs leading-relaxed text-slate-500">
                  <MessageSquareText className="mt-0.5 h-3.5 w-3.5 shrink-0 text-slate-400" aria-hidden="true" />
                  <span>{entry.deliveryNote}</span>
                </p>
              )}
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}
