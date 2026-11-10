import { BookingFormData, DeliveryTier, ParcelSize } from '@/types/booking';

// Sri Lankan Phone Number regex: accepts 07XXXXXXXX or +947XXXXXXXX or 0XXXXXXXXX (matches backend regex ^(\+94|0)[0-9]{9}$)
export const SRI_LANKAN_PHONE_REGEX = /^(?:\+94|0)[0-9]{9}$/;

export const INITIAL_FORM_DATA: BookingFormData = {
  senderName: '',
  senderPhone: '',
  senderAddress: '',
  originZoneId: '',
  recipientName: '',
  recipientPhone: '',
  recipientAddress: '',
  destinationZoneId: '',
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
