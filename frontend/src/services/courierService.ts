/**
 * SwiftDrop Courier Execution API Client Service (Member 6)
 * Communicates with backend parcel-service endpoints via API Gateway.
 */

import { api } from '@/lib/api';
import { authStorage } from '@/lib/auth';
import {
  CourierParcelCard,
  CourierRunsheetResponse,
  CourierHistoryResponse,
  ParcelStatusUpdateResponse,
  ProofUploadResponse,
  UpdateParcelStatusRequest,
} from '@/types/courier';

/**
 * Helper to build headers with courier identity.
 */
function getCourierHeaders(): Record<string, string> {
  const user = authStorage.getUser();
  const headers: Record<string, string> = {};
  if (user?.userId) {
    headers['X-Courier-Id'] = user.userId;
    headers['X-User-Id'] = user.userId;
  }
  return headers;
}

/**
 * Retrieves the daily partitioned runsheet (pickups & deliveries) for the logged-in courier.
 */
export async function getCourierRunsheet(): Promise<CourierRunsheetResponse> {
  const res = await api.get<CourierRunsheetResponse>('/api/v1/parcels/courier/runsheet', {
    headers: getCourierHeaders(),
  });
  return res.data;
}

/**
 * Retrieves details for a specific parcel assigned to the logged-in courier.
 * Enforces ownership check on the backend (HTTP 403 if assigned to another courier).
 */
export async function getCourierParcelById(parcelId: string): Promise<CourierParcelCard> {
  const res = await api.get<CourierParcelCard>(`/api/v1/parcels/courier/${parcelId}`, {
    headers: getCourierHeaders(),
  });
  return res.data;
}

/**
 * Executes a lifecycle state transition (PICKED_UP, OUT_FOR_DELIVERY, DELIVERED, FAILED)
 * with backend state machine validation.
 */
export async function updateParcelStatus(
  parcelId: string,
  request: UpdateParcelStatusRequest
): Promise<ParcelStatusUpdateResponse> {
  const res = await api.patch<ParcelStatusUpdateResponse>(
    `/api/v1/parcels/${parcelId}/status`,
    request,
    {
      headers: getCourierHeaders(),
    }
  );
  return res.data;
}

/**
 * Uploads a delivery proof image (doorstep photo or canvas signature PNG) to S3/local storage.
 */
export async function uploadDeliveryProof(
  parcelId: string,
  file: File | Blob,
  filename: string = 'proof.png'
): Promise<ProofUploadResponse> {
  const formData = new FormData();
  formData.append('file', file, filename);

  const res = await api.post<ProofUploadResponse>(
    `/api/v1/parcels/${parcelId}/proof-upload`,
    formData,
    {
      headers: {
        ...getCourierHeaders(),
        'Content-Type': 'multipart/form-data',
      },
    }
  );
  return res.data;
}

/**
 * Retrieves the historical completed, failed, and returned deliveries for the logged-in courier.
 */
export async function getCourierHistory(): Promise<CourierHistoryResponse> {
  const res = await api.get<CourierHistoryResponse>('/api/v1/parcels/courier/history', {
    headers: getCourierHeaders(),
  });
  return res.data;
}
