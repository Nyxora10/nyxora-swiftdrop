import { api } from '@/lib/api';
import {
  BookingFormData,
  BookingResponse,
  CustomerBookingPayload,
  ParcelQuotePayload,
  RateQuoteResponse,
} from '@/types/booking';

/**
 * Service to calculate dynamic rate quotes for shipments.
 */
export async function getRateQuote(formData: BookingFormData): Promise<RateQuoteResponse> {
  const payload: ParcelQuotePayload = {
    originZoneId: Number(formData.originZoneId) || 1,
    destinationZoneId: Number(formData.destinationZoneId) || 2,
    weightKg: Number(formData.weightKg),
    parcelSize: formData.parcelSize,
    tier: formData.deliveryTier,
    deliveryTier: formData.deliveryTier,
  };

  const res = await api.post('/api/v1/parcels/quote', payload);
  const raw = res.data;

  return {
    baseRate: Number(raw.baseFee ?? raw.baseRate ?? 350),
    weightSurcharge: Number(raw.weightCharge ?? raw.weightSurcharge ?? 0),
    sizeSurcharge: Number(raw.zoneSurcharge ?? raw.sizeSurcharge ?? 0),
    tierMultiplier: Number(raw.sizeMultiplier ?? 1.0),
    totalEstimatedFee: Number(raw.totalFee ?? raw.totalEstimatedFee ?? 0),
    currency: 'LKR',
    estimatedDeliveryHours: formData.deliveryTier === 'EXPRESS' ? 24 : 48,
  };
}

/**
 * Service to book a parcel shipment and register it with dispatch.
 */
export async function bookParcel(formData: BookingFormData): Promise<BookingResponse> {
  const payload: CustomerBookingPayload = {
    originZoneId: Number(formData.originZoneId) || 1,
    destinationZoneId: Number(formData.destinationZoneId) || 2,
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
    status: raw.currentStatus || raw.status || 'CREATED',
    originZoneName: raw.originZoneName || 'Origin Zone',
    destinationZoneName: raw.destinationZoneName || 'Destination Zone',
    totalChargeLkr: Number(raw.estimatedFee ?? raw.totalChargeLkr ?? 0),
    deliveryTier: raw.appliedTier || raw.deliveryTier || formData.deliveryTier,
    createdAt: raw.createdAt || new Date().toISOString(),
  };
}

/**
 * Service to download the official delivery receipt PDF for a booked parcel.
 */
export async function downloadReceiptPdf(parcelId: string): Promise<Blob> {
  const response = await api.get(`/api/v1/parcels/${parcelId}/receipt/pdf`, {
    responseType: 'blob',
  });
  return new Blob([response.data], { type: 'application/pdf' });
}

/**
 * Generates an offline client-side PDF document for demo simulation.
 */
export function generateDemoPdf(bookingResult: BookingResponse): Blob {
  const samplePdfContent = `%PDF-1.4
1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj
3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj
4 0 obj << /Length 210 >> stream
BT
/F1 18 Tf
50 720 Td (SwiftDrop Express Logistics Delivery Receipt) Tj
/F1 12 Tf
0 -30 Td (Tracking Number: ${bookingResult.trackingNumber}) Tj
0 -20 Td (Service Level: ${bookingResult.deliveryTier}) Tj
0 -20 Td (Total Paid: LKR ${Number(bookingResult.totalChargeLkr).toFixed(2)}) Tj
0 -20 Td (Status: CREATED - Registered in Dispatch Network) Tj
ET
endstream
endobj
5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj
xref
0 6
0000000000 65535 f 
0000000009 00000 n 
0000000058 00000 n 
0000000115 00000 n 
0000000244 00000 n 
0000000506 00000 n 
trailer << /Size 6 /Root 1 0 R >>
startxref
577
%%EOF`;
  return new Blob([samplePdfContent], { type: 'application/pdf' });
}

