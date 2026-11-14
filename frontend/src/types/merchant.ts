/**
 * Merchant portal types (Member 2).
 * Shapes mirror the auth-service merchant profile API and the parcel-service merchant order APIs.
 */

export type ParcelSize = 'SMALL' | 'MEDIUM' | 'LARGE';

export type MerchantParcelStatus =
  | 'CREATED'
  | 'ASSIGNED'
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'FAILED'
  | 'RETURNED_TO_SENDER';

/** parcel-service OrderSummaryDto (GET /api/v1/parcels/merchant/orders → orders.content[]) */
export interface MerchantOrder {
  parcelId: string;
  trackingNumber: string;
  recipientName: string;
  recipientPhone: string;
  dropoffAddress: string;
  originZoneName?: string | null;
  destinationZoneName?: string | null;
  weightKg: number;
  parcelSize: ParcelSize;
  estimatedFee: number;
  /** Merchant discount applied to estimatedFee; null for orders booked before discounts were recorded. */
  discountAmount?: number | null;
  currentStatus: MerchantParcelStatus;
  createdAt: string;
}

export interface PagedOrders<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  isFirst?: boolean;
  isLast?: boolean;
  first?: boolean;
  last?: boolean;
}

/** parcel-service MerchantOrderHistoryResponse */
export interface MerchantOrderHistory {
  totalOrders: number;
  inTransitCount: number;
  deliveredCount: number;
  failedCount: number;
  /** Lifetime merchant discount in LKR. */
  totalSavings: number;
  orders: PagedOrders<MerchantOrder>;
}

/** auth-service MerchantProfileResponse */
export interface MerchantProfile {
  merchantId: string;
  businessName: string;
  registrationNumber: string;
  oauthClientId: string;
  webhookUrl: string | null;
  /** Masked by the server; the plaintext is only returned once as newWebhookSecret. */
  webhookSecret: string | null;
  newWebhookSecret?: string | null;
}

/** notification-webhook-service DemoWebhookEvent */
export interface WebhookEvent {
  id: string;
  merchantId: string;
  eventType: string;
  payload: string;
  receivedAt: string;
  verified: boolean;
  verificationStatus: string;
}

export interface WebhookEventsResponse {
  merchantId: string;
  count: number;
  events: WebhookEvent[];
}

/** One order as sent to POST /api/v1/parcels/bulk */
export interface BulkOrderInput {
  originZoneId: number;
  destinationZoneId: number;
  pickupAddress: string;
  dropoffAddress: string;
  recipientName: string;
  recipientPhone: string;
  weightKg: number;
  parcelSize: ParcelSize;
  merchantOrderReference?: string;
}

export interface BulkResultItem {
  rowNumber: number;
  trackingNumber: string | null;
  estimatedFee: number | null;
  status: 'SUCCESS' | 'FAILED';
  merchantOrderReference?: string;
  errors: string[];
}

/** parcel-service BulkIngestionResponse */
export interface BulkIngestionResponse {
  totalReceived: number;
  successfulCount: number;
  failedCount: number;
  results: BulkResultItem[];
}
