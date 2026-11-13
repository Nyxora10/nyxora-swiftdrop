/**
 * SwiftDrop Courier Domain Types & DTO Definitions (Member 6)
 * Maps to backend DTOs in com.nyxora.swiftdrop.parcel.courier.dto
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

export type DeliveryFailureReason =
  | 'RECIPIENT_UNAVAILABLE'
  | 'INCORRECT_ADDRESS'
  | 'SECURITY_ACCESS_RESTRICTED'
  | 'CUSTOMER_REJECTED'
  | 'DAMAGED_PARCEL'
  | 'WEATHER_OR_ROAD_BLOCK'
  | 'OTHER';

export interface CourierParcelCard {
  id: string;
  trackingNumber: string;
  currentStatus: ParcelStatus;
  originZoneName?: string | null;
  destinationZoneName?: string | null;
  pickupAddress: string;
  dropoffAddress: string;
  recipientName: string;
  recipientPhone: string;
  weightKg: number;
  parcelSize: ParcelSize;
  estimatedFee: number;
  failureCount: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CourierRunsheetResponse {
  courierId: string;
  totalActiveParcels: number;
  pickups: CourierParcelCard[];
  deliveries: CourierParcelCard[];
  generatedAt: string;
}

export interface UpdateParcelStatusRequest {
  targetStatus: ParcelStatus;
  deliveryNote?: string;
  recipientSignatureName?: string;
  proofPhotoUrl?: string;
  failureReason?: string;
}

export interface ParcelStatusUpdateResponse {
  parcelId: string;
  trackingNumber: string;
  previousStatus: ParcelStatus;
  currentStatus: ParcelStatus;
  updatedAt: string;
  message: string;
}

export interface ProofUploadResponse {
  parcelId: string;
  filename: string;
  proofPhotoUrl: string;
  uploadedAt: string;
}

export interface CourierDeliveryHistoryItem {
  parcelId: string;
  trackingNumber: string;
  finalStatus: ParcelStatus;
  recipientName: string;
  recipientPhone: string;
  dropoffAddress: string;
  destinationZoneName?: string | null;
  weightKg: number;
  parcelSize: ParcelSize;
  estimatedFee: number;
  recipientSignatureName?: string | null;
  proofPhotoUrl?: string | null;
  deliveryNote?: string | null;
  failureReason?: string | null;
  completedAt?: string | null;
}

export interface CourierHistoryResponse {
  courierId: string;
  totalCompleted: number;
  deliveredCount: number;
  failedCount: number;
  returnedCount: number;
  history: CourierDeliveryHistoryItem[];
  generatedAt: string;
}
