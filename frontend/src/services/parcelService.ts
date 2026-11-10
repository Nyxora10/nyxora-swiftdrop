import { api } from '@/lib/api';
import {
  BookingFormData,
  BookingResponse,
  CustomerBookingPayload,
  ParcelQuotePayload,
  RateQuoteResponse,
} from '@/types/booking';
import { ParcelTrackingResponse } from '@/types/tracking';

/**
 * Retrieves the public tracking snapshot for a parcel.
 * Calls GET /api/v1/parcels/track/{trackingNumber}.
 */
export async function getParcelTracking(trackingNumber: string): Promise<ParcelTrackingResponse> {
  const normalizedTrackingNumber = trackingNumber.trim();

  if (!normalizedTrackingNumber) {
    throw new Error('A tracking number is required.');
  }

  const response = await api.get<ParcelTrackingResponse>(
    `/api/v1/parcels/track/${encodeURIComponent(normalizedTrackingNumber)}`
  );
  return response.data;
}

/**
 * Service to calculate dynamic rate quotes for shipments.
 * Calls POST /api/v1/parcels/quote directly against the backend Pricing Engine.
 */
export async function getRateQuote(formData: BookingFormData): Promise<RateQuoteResponse> {
  const originId = Number(formData.originZoneId);
  const destId = Number(formData.destinationZoneId);

  if (!originId || !destId) {
    throw new Error('Valid origin and destination delivery zones must be selected.');
  }

  const payload: ParcelQuotePayload = {
    originZoneId: originId,
    destinationZoneId: destId,
    weightKg: Number(formData.weightKg),
    parcelSize: formData.parcelSize,
    tier: formData.deliveryTier,
  };

  const res = await api.post('/api/v1/parcels/quote', payload);
  const raw = res.data;

  const baseRate = Number(raw.baseFee ?? 0);
  const weightSurcharge = Number(raw.weightCharge ?? 0);
  const sizeMultiplier = Number(raw.sizeMultiplier ?? 1.0);
  const zoneSurcharge = Number(raw.zoneSurcharge ?? 0);
  const expressSurcharge = Number(raw.expressSurcharge ?? 0);
  const discountAmount = Number(raw.discountAmount ?? 0);

  // Incremental volumetric size surcharge: (baseRate + weightSurcharge) * (sizeMultiplier - 1.0)
  const routeSubtotal = baseRate + weightSurcharge;
  const sizeSurcharge = Math.round(routeSubtotal * (sizeMultiplier - 1.0) * 100) / 100;

  return {
    baseRate,
    weightSurcharge,
    zoneSurcharge,
    sizeMultiplier,
    expressSurcharge,
    discountAmount,
    sizeSurcharge,
    tierMultiplier: sizeMultiplier,
    totalEstimatedFee: Number(raw.totalFee),
    currency: 'LKR',
    estimatedDeliveryHours: formData.deliveryTier === 'EXPRESS' ? 24 : 48,
  };
}

/**
 * Service to book a parcel shipment and register it with dispatch.
 * Calls POST /api/v1/parcels/book.
 *
 * NOTE: specialInstructions, senderName, and senderPhone are collected for client-side
 * receipt rendering and courier dispatch label printout, but are not accepted by the backend
 * CustomerBookingRequest contract.
 */
export async function bookParcel(formData: BookingFormData): Promise<BookingResponse> {
  const originId = Number(formData.originZoneId);
  const destId = Number(formData.destinationZoneId);

  if (!originId || !destId) {
    throw new Error('Valid origin and destination delivery zones are required to book dispatch.');
  }

  const payload: CustomerBookingPayload = {
    originZoneId: originId,
    destinationZoneId: destId,
    pickupAddress: formData.senderAddress.trim(),
    dropoffAddress: formData.recipientAddress.trim(),
    recipientName: formData.recipientName.trim(),
    recipientPhone: formData.recipientPhone.trim(),
    weightKg: Number(formData.weightKg),
    parcelSize: formData.parcelSize,
    tier: formData.deliveryTier,
  };

  const res = await api.post('/api/v1/parcels/book', payload);
  const raw = res.data;

  return {
    parcelId: raw.parcelId,
    trackingNumber: raw.trackingNumber,
    status: raw.currentStatus || 'CREATED',
    originZoneName: raw.originZoneName || '',
    destinationZoneName: raw.destinationZoneName || '',
    totalChargeLkr: Number(raw.estimatedFee),
    deliveryTier: raw.appliedTier || formData.deliveryTier,
    createdAt: raw.createdAt || new Date().toISOString(),
  };
}

/**
 * Service to download the official delivery receipt PDF for a booked parcel.
 * Streams binary application/pdf from GET /api/v1/parcels/{id}/receipt/pdf.
 */
export async function downloadReceiptPdf(parcelId: string): Promise<Blob> {
  const response = await api.get(`/api/v1/parcels/${parcelId}/receipt/pdf`, {
    responseType: 'blob',
  });
  return new Blob([response.data], { type: 'application/pdf' });
}
