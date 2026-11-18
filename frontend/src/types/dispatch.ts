/**
 * SwiftDrop Dispatch & Operations Type Definitions (Member 5: Tasks 1.6 & 2.2)
 * Matches backend DTOs in parcel-service and auth-service.
 */

export type ParcelSize = 'SMALL' | 'MEDIUM' | 'LARGE';

export type ParcelStatus =
  | 'CREATED'
  | 'ASSIGNED'
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'DELIVERED'
  | 'FAILED'
  | 'CANCELLED';

/**
 * Representation of an unassigned parcel awaiting courier dispatch.
 * Matches com.nyxora.swiftdrop.parcel.dispatch.dto.UnassignedParcelResponse.
 */
export interface UnassignedParcelResponse {
  id: string;
  trackingNumber: string;
  pickupAddress: string;
  dropoffAddress: string;
  recipientName: string;
  recipientPhone: string;
  weightKg: number;
  parcelSize: ParcelSize;
  estimatedFee: number;
  currentStatus: ParcelStatus;
  originZoneId: number | null;
  originZoneCode: string | null;
  originZoneName: string | null;
  destinationZoneId: number | null;
  destinationZoneCode: string | null;
  destinationZoneName: string | null;
  createdAt: string;
}

/**
 * Representation of an active on-duty courier and their workload capacity.
 * Matches com.nyxora.swiftdrop.auth.dto.response.AvailableCourierResponse.
 */
export interface AvailableCourierResponse {
  courierId: string;
  userId: string;
  fullName: string;
  email: string;
  phoneNumber?: string | null;
  vehicleType: string | null;
  licensePlate: string;
  activeDeliveryCount: number;
  maxWorkload: number;
  assignedZoneId: number | null;
  isAvailable: boolean;
  canAssign: boolean;
}

/**
 * Payload sent to assign a courier to an unassigned parcel.
 * Matches com.nyxora.swiftdrop.parcel.dispatch.dto.AssignCourierRequest.
 */
export interface AssignCourierRequest {
  courierId: string;
}

/**
 * Confirmation payload returned after successful courier allocation.
 * Matches com.nyxora.swiftdrop.parcel.dispatch.dto.AssignCourierResponse.
 */
export interface AssignCourierResponse {
  parcelId: string;
  trackingNumber: string;
  assignedCourierId: string;
  status: string;
  assignedAt: string;
  message: string;
}

