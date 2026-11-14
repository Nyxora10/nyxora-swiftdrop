export type ParcelStatus =
  | 'CREATED'
  | 'ASSIGNED'
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'FAILED'
  | 'RETURNED_TO_SENDER';

export interface TrackingTimelineEvent {
  status: ParcelStatus;
  timestamp: string;
  deliveryNote?: string | null;
}

export interface ParcelTrackingResponse {
  trackingNumber: string;
  currentStatus: ParcelStatus;
  originZoneCode: string;
  originZoneName: string;
  destinationZoneCode: string;
  destinationZoneName: string;
  maskedRecipientName: string;
  maskedRecipientPhone: string;
  maskedDropoffAddress: string;
  weightKg: number;
  parcelSize: 'SMALL' | 'MEDIUM' | 'LARGE';
  createdAt: string;
  timeline: TrackingTimelineEvent[];
}

export interface ParcelTrackingUpdate {
  trackingNumber: string;
  currentStatus: ParcelStatus;
  statusDescription?: string | null;
  currentZone?: string | null;
  recipientNameMasked?: string | null;
  deliveryNote?: string | null;
  proofPhotoUrl?: string | null;
  timestamp: string;
}
