import type { MerchantParcelStatus } from '@/types/merchant';

/** "LKR 1,234.50"; a dash for missing values. */
export function formatLkr(value: number | string | null | undefined): string {
  const amount = Number(value);
  if (value === null || value === undefined || value === '' || !Number.isFinite(amount)) return '—';
  return `LKR ${amount.toLocaleString('en-LK', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

/** True for an http(s) URL, or an empty value (which removes the webhook). */
export function isValidWebhookUrl(value: string): boolean {
  if (!value.trim()) return true;
  try {
    const url = new URL(value.trim());
    return (url.protocol === 'https:' || url.protocol === 'http:') && Boolean(url.hostname);
  } catch {
    return false;
  }
}

/** Reads the server's problem-detail message from an axios error, or falls back to the given text. */
export function apiErrorMessage(err: unknown, fallback: string): string {
  const data = (err as { response?: { data?: { detail?: string; message?: string } } })?.response?.data;
  return data?.detail || data?.message || fallback;
}

export const STATUS_LABELS: Record<MerchantParcelStatus, string> = {
  CREATED: 'Created',
  ASSIGNED: 'Assigned',
  PICKED_UP: 'Picked up',
  IN_TRANSIT: 'In transit',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERED: 'Delivered',
  FAILED: 'Failed',
  RETURNED_TO_SENDER: 'Returned',
};

export const STATUS_STYLES: Record<MerchantParcelStatus, string> = {
  CREATED: 'bg-slate-100 text-slate-700 border-slate-200',
  ASSIGNED: 'bg-indigo-50 text-indigo-700 border-indigo-200',
  PICKED_UP: 'bg-blue-50 text-blue-700 border-blue-200',
  IN_TRANSIT: 'bg-amber-50 text-amber-700 border-amber-200',
  OUT_FOR_DELIVERY: 'bg-purple-50 text-purple-700 border-purple-200',
  DELIVERED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  FAILED: 'bg-rose-50 text-rose-700 border-rose-200',
  RETURNED_TO_SENDER: 'bg-orange-50 text-orange-700 border-orange-200',
};
