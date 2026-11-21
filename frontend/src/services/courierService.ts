/**
 * SwiftDrop Courier Execution API Client Service (Member 6)
 * Communicates with backend parcel-service endpoints via API Gateway.
 */

import axios from 'axios';
import { api } from '@/lib/api';
import { authStorage } from '@/lib/auth';
import {
  CourierParcelCard,
  CourierRunsheetResponse,
  CourierHistoryResponse,
  ParcelStatusUpdateResponse,
  ProofUploadResponse,
  PresignedProofResponse,
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
 * Uploads a delivery proof image (doorstep photo or canvas signature PNG).
 *
 * Enterprise Pattern (Task 1.8):
 * 1. Primary: Fetches a short-lived AWS S3 Pre-signed URL and uploads directly
 *    from the client to S3 via HTTP PUT (zero server CPU/memory/bandwidth overhead).
 * 2. Fallback: If S3 is unconfigured (local storage mode) or direct upload encounters
 *    a network issue, automatically and gracefully falls back to the Spring Boot multipart endpoint.
 */
export async function uploadDeliveryProof(
  parcelId: string,
  file: File | Blob,
  filename: string = 'proof.png'
): Promise<ProofUploadResponse> {
  const contentType =
    file.type || (filename.endsWith('.jpg') || filename.endsWith('.jpeg') ? 'image/jpeg' : 'image/png');

  // 1. Try Direct S3 Upload via Pre-signed URL
  try {
    const presignedRes = await api.get<PresignedProofResponse>(
      `/api/v1/parcels/${parcelId}/proof-presigned-url`,
      {
        params: { filename, contentType },
        headers: getCourierHeaders(),
      }
    );

    if (presignedRes.status === 200 && presignedRes.data?.uploadUrl) {
      const { uploadUrl, publicFileUrl, filename: s3Filename } = presignedRes.data;

      // Direct HTTP PUT to AWS S3 bucket (with CORS enabled)
      await axios.put(uploadUrl, file, {
        headers: {
          'Content-Type': contentType,
        },
      });

      return {
        parcelId,
        filename: s3Filename,
        proofPhotoUrl: publicFileUrl,
        uploadedAt: new Date().toISOString(),
      };
    }
  } catch (err) {
    console.warn(
      '[CourierService] Direct S3 upload unavailable or failed, falling back to server multipart:',
      err
    );
  }

  // 2. Resilient Fallback: Spring Boot Multipart Upload
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
