import { BookingFormData, DeliveryTier, DeliveryZone, ParcelSize, RateQuoteResponse } from '@/types/booking';

// Sri Lankan Phone Number regex: 10 digits starting with 07
export const SRI_LANKAN_PHONE_REGEX = /^07\d{8}$/;

// Pre-seeded fallback zones representing major Sri Lankan logistics hubs
export const DEFAULT_FALLBACK_ZONES: DeliveryZone[] = [
  { id: '1', zoneCode: 'WP-COL-01', zoneName: 'Colombo Central Metro', district: 'Colombo' },
  { id: '2', zoneCode: 'WP-GAM-01', zoneName: 'Gampaha Industrial Hub', district: 'Gampaha' },
  { id: '3', zoneCode: 'CP-KAN-01', zoneName: 'Kandy Central Highlands', district: 'Kandy' },
  { id: '4', zoneCode: 'SP-GAL-01', zoneName: 'Galle Southern Corridor', district: 'Galle' },
  { id: '5', zoneCode: 'NP-JAF-01', zoneName: 'Jaffna Northern Peninsula', district: 'Jaffna' },
  { id: '6', zoneCode: 'NW-KUR-01', zoneName: 'Kurunegala Wayamba Zone', district: 'Kurunegala' },
];

export const INITIAL_FORM_DATA: BookingFormData = {
  senderName: '',
  senderPhone: '',
  senderAddress: '',
  originZoneId: DEFAULT_FALLBACK_ZONES[0].id,
  recipientName: '',
  recipientPhone: '',
  recipientAddress: '',
  destinationZoneId: DEFAULT_FALLBACK_ZONES[1].id,
  weightKg: 1.5,
  parcelSize: 'MEDIUM',
  deliveryTier: 'STANDARD',
  specialInstructions: '',
};

export const WEIGHT_PILLS: number[] = [0.5, 1.0, 2.0, 3.5, 5.0, 10.0];

export interface SizeOption {
  id: ParcelSize;
  title: string;
  dims: string;
  examples: string;
  mult: string;
  multiplierValue: number;
}

export const SIZE_OPTIONS: SizeOption[] = [
  {
    id: 'SMALL',
    title: 'Small Parcel',
    dims: 'Up to 25 × 15 × 5 cm',
    examples: 'Envelopes, documents, phones, small jewelry',
    mult: '1.0x Base',
    multiplierValue: 1.0,
  },
  {
    id: 'MEDIUM',
    title: 'Medium Box',
    dims: 'Up to 40 × 30 × 20 cm',
    examples: 'Shoebox, apparel, electronics, books',
    mult: '1.2x Multiplier',
    multiplierValue: 1.2,
  },
  {
    id: 'LARGE',
    title: 'Large / Bulky Crate',
    dims: 'Up to 60 × 50 × 40 cm',
    examples: 'Appliances, bulk cartons, machinery parts',
    mult: '1.5x Multiplier',
    multiplierValue: 1.5,
  },
];

export interface TierOption {
  id: DeliveryTier;
  title: string;
  badge: string;
  tag?: string;
  description: string;
  transitTime: string;
  multiplierValue: number;
  deliveryHours: number;
}

export const TIER_OPTIONS: TierOption[] = [
  {
    id: 'STANDARD',
    title: 'Standard Delivery',
    badge: 'Economy',
    description: 'Reliable scheduled transit across Sri Lankan districts within 48 to 72 hours.',
    transitTime: 'Estimated 48-72 Hours Transit',
    multiplierValue: 1.0,
    deliveryHours: 48,
  },
  {
    id: 'EXPRESS',
    title: 'Express Priority',
    badge: '+50% Tier',
    tag: 'PRIORITY EXPRESS',
    description: 'Guaranteed next-day expedited delivery with priority sorting and dispatch.',
    transitTime: 'Guaranteed Next-Day (Within 24 Hours)',
    multiplierValue: 1.5,
    deliveryHours: 24,
  },
];

export const PRICING_CONFIG = {
  INTRA_ZONE_BASE: 300.0,
  INTER_ZONE_BASE: 450.0,
  PER_KG_SURCHARGE: 80.0,
  FREE_WEIGHT_KG: 1.0,
} as const;

/**
 * Pure calculation function for resilient offline fallback pricing
 */
export function calculateFallbackQuote(formData: BookingFormData): RateQuoteResponse {
  const isInterZone = formData.originZoneId !== formData.destinationZoneId;
  const baseFare = isInterZone ? PRICING_CONFIG.INTER_ZONE_BASE : PRICING_CONFIG.INTRA_ZONE_BASE;
  const weightSurcharge = Math.max(0, formData.weightKg - PRICING_CONFIG.FREE_WEIGHT_KG) * PRICING_CONFIG.PER_KG_SURCHARGE;
  
  const sizeOption = SIZE_OPTIONS.find((s) => s.id === formData.parcelSize) ?? SIZE_OPTIONS[1];
  const tierOption = TIER_OPTIONS.find((t) => t.id === formData.deliveryTier) ?? TIER_OPTIONS[0];

  const sizeMultiplier = sizeOption.multiplierValue;
  const tierMultiplier = tierOption.multiplierValue;
  const subtotal = (baseFare + weightSurcharge) * sizeMultiplier;
  const total = subtotal * tierMultiplier;

  return {
    baseRate: baseFare,
    weightSurcharge,
    sizeSurcharge: subtotal - (baseFare + weightSurcharge),
    tierMultiplier,
    totalEstimatedFee: Math.round(total * 100) / 100,
    currency: 'LKR',
    estimatedDeliveryHours: tierOption.deliveryHours,
  };
}

