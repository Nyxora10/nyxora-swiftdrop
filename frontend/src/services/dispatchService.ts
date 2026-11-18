/**
 * SwiftDrop Admin Dispatch API Client Service (Member 5: Tasks 1.6 & 2.2)
 * Handles unassigned parcel queues, courier availability lookups, and assignment workflows.
 */

import { api } from '@/lib/api';
import { authStorage } from '@/lib/auth';
import {
  UnassignedParcelResponse,
  AvailableCourierResponse,
  AssignCourierRequest,
  AssignCourierResponse,
} from '@/types/dispatch';

/**
 * Retrieves unassigned parcels in CREATED status awaiting courier dispatch.
 * Optionally filters by origin delivery zone ID.
 *
 * @param originZoneId Optional zone ID to filter unassigned parcels
 */
export async function getUnassignedParcels(
  originZoneId?: number | null
): Promise<UnassignedParcelResponse[]> {
  const params: Record<string, string> = {};
  if (originZoneId !== undefined && originZoneId !== null) {
    params.originZoneId = String(originZoneId);
  }

  const response = await api.get<UnassignedParcelResponse[]>('/api/v1/parcels/unassigned', {
    params,
  });
  return response.data;
}

/**
 * Retrieves active on-duty couriers with their current active delivery counts and workload capacity.
 * Optionally filters by assigned delivery zone ID.
 *
 * @param zoneId Optional zone ID to filter available couriers
 */
export async function getAvailableCouriers(
  zoneId?: number | null
): Promise<AvailableCourierResponse[]> {
  const params: Record<string, string> = {};
  if (zoneId !== undefined && zoneId !== null) {
    params.zoneId = String(zoneId);
  }

  const response = await api.get<AvailableCourierResponse[]>('/api/v1/auth/admin/couriers/available', {
    params,
  });
  return response.data;
}

/**
 * Assigns an unassigned parcel to a selected courier.
 * Automatically injects X-User-Id header for administrative assignment auditing.
 *
 * @param parcelId UUID of the parcel to assign
 * @param courierId UUID of the target courier
 */
export async function assignCourier(
  parcelId: string,
  courierId: string
): Promise<AssignCourierResponse> {
  const payload: AssignCourierRequest = { courierId };
  const user = authStorage.getUser();
  const headers: Record<string, string> = {};
  if (user?.userId) {
    headers['X-User-Id'] = user.userId;
  }

  const response = await api.put<AssignCourierResponse>(
    `/api/v1/parcels/${parcelId}/assign`,
    payload,
    { headers }
  );
  return response.data;
}

