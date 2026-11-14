import { api } from '@/lib/api';
import { authStorage } from '@/lib/auth';
import {
  BookingFormData,
  BookingResponse,
  CustomerBookingPayload,
  ParcelQuotePayload,
  RateQuoteResponse,
} from '@/types/booking';
import { ParcelTrackingResponse } from '@/types/tracking';
import {
  CustomerOrderQuery,
  OrderSummary,
  PagedOrderResponse,
} from '@/types/order';

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
 * Helper to build authenticated customer identity headers.
 * Supplies X-User-Id and X-User-Role for direct microservice gateway alignment.
 */
function getCustomerAuthHeaders(): Record<string, string> {
  const user = authStorage.getUser();
  const headers: Record<string, string> = {};
  if (user?.userId) {
    headers['X-User-Id'] = user.userId;
  }
  if (user?.role) {
    headers['X-User-Role'] = user.role;
  }
  return headers;
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

  const res = await api.post('/api/v1/parcels/book', payload, {
    headers: getCustomerAuthHeaders(),
  });
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
 * Service to retrieve paginated order history for the authenticated customer.
 * Calls GET /api/v1/parcels/customer/orders?page={page}&size={size}&status={status}.
 */
export async function getCustomerOrders(
  params: CustomerOrderQuery = {}
): Promise<PagedOrderResponse<OrderSummary>> {
  const queryParams: Record<string, string | number> = {
    page: Math.max(0, params.page ?? 0),
    size: Math.max(1, Math.min(params.size ?? 10, 50)),
  };

  if (params.status) {
    queryParams.status = params.status;
  }

  const response = await api.get<PagedOrderResponse<OrderSummary>>(
    '/api/v1/parcels/customer/orders',
    {
      params: queryParams,
      headers: getCustomerAuthHeaders(),
    }
  );

  return response.data;
}

/**
 * Service to download the official delivery receipt PDF for a booked parcel.
 * Streams binary application/pdf from GET /api/v1/parcels/{id}/receipt/pdf.
 * Injects authenticated headers so customer can access their own invoice.
 */
export async function downloadReceiptPdf(parcelId: string): Promise<Blob> {
  const response = await api.get(`/api/v1/parcels/${parcelId}/receipt/pdf`, {
    headers: getCustomerAuthHeaders(),
    responseType: 'blob',
  });
  return new Blob([response.data], { type: 'application/pdf' });
}
