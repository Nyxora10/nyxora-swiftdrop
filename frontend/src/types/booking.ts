export type ParcelSize = 'SMALL' | 'MEDIUM' | 'LARGE';
export type DeliveryTier = 'STANDARD' | 'EXPRESS';

export interface DeliveryZone {
  id: string;
  zoneCode: string;
  zoneName: string;
  district: string;
  baseSurcharge?: number;
}

export interface BookingFormData {
  senderName: string;
  senderPhone: string;
  senderAddress: string;
  originZoneId: string;
  recipientName: string;
  recipientPhone: string;
  recipientAddress: string;
  destinationZoneId: string;
  weightKg: number;
  parcelSize: ParcelSize;
  deliveryTier: DeliveryTier;
  specialInstructions: string;
}

export interface RateQuoteResponse {
  baseRate: number;
  weightSurcharge: number;
  sizeSurcharge: number;
  tierMultiplier: number;
  totalEstimatedFee: number;
  currency: string;
  estimatedDeliveryHours: number;
}

export interface BookingResponse {
  parcelId: string;
  trackingNumber: string;
  status: string;
  originZoneName: string;
  destinationZoneName: string;
  totalChargeLkr: number;
  deliveryTier: string;
  estimatedDeliveryDate?: string;
  createdAt: string;
}

/**
 * Backend API Payload DTO for POST /api/v1/parcels/quote
 */
export interface ParcelQuotePayload {
  originZoneId: number;
  destinationZoneId: number;
  weightKg: number;
  parcelSize: ParcelSize;
  tier: DeliveryTier;
  deliveryTier?: DeliveryTier;
}

/**
 * Backend API Payload DTO for POST /api/v1/parcels/book
 */
export interface CustomerBookingPayload {
  originZoneId: number;
  destinationZoneId: number;
  pickupAddress: string;
  dropoffAddress: string;
  recipientName: string;
  recipientPhone: string;
  weightKg: number;
  parcelSize: ParcelSize;
  tier: DeliveryTier;
}

