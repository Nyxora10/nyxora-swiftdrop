/**
 * SwiftDrop Logistics — Customer Order History & Lifecycle Data Contracts
 * Member 4 Domain Specification
 *
 * Directly mirrors backend microservice contracts:
 * - com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus
 * - com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize
 * - com.nyxora.swiftdrop.parcel.history.dto.OrderSummaryDto
 * - com.nyxora.swiftdrop.parcel.history.dto.PagedOrderResponse
 */

export type ParcelStatus =
  | 'CREATED'
  | 'ASSIGNED'
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'FAILED'
  | 'RETURNED_TO_SENDER';

export type ParcelSize = 'SMALL' | 'MEDIUM' | 'LARGE';

/**
 * Filter categories for customer history tab navigation
 */
export type OrderStatusFilter =
  | 'ALL'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'CREATED'
  | 'FAILED_OR_RETURNED';

/**
 * Summary DTO of a parcel delivery order for customer and merchant listings.
 */
export interface OrderSummary {
  parcelId: string;
  trackingNumber: string;
  currentStatus: ParcelStatus;
  recipientName: string;
  recipientPhone: string;
  dropoffAddress: string;
  originZoneName: string;
  destinationZoneName: string;
  weightKg: number;
  parcelSize: ParcelSize;
  estimatedFee: number;
  currency: string;
  createdAt: string;
  updatedAt?: string;
}

/**
 * Generic paginated response envelope for order history queries.
 */
export interface PagedOrderResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  isFirst: boolean;
  isLast: boolean;
}

/**
 * Request query parameters for customer order history queries.
 */
export interface CustomerOrderQuery {
  page?: number;
  size?: number;
  status?: ParcelStatus;
  search?: string;
}
