import { api } from '@/lib/api';
import { DeliveryZone } from '@/types/booking';

export interface PublicDeliveryZoneResponse {
  id: number;
  zoneCode: string;
  zoneName: string;
  district: string;
}

/**
 * Service to fetch active logistics delivery zones across Sri Lanka.
 * Calls the public, unauthenticated lookup endpoint: GET /api/v1/public/zones.
 */
export async function getDeliveryZones(search?: string, district?: string): Promise<DeliveryZone[]> {
  const params: Record<string, string> = {};
  if (search?.trim()) params.search = search.trim();
  if (district?.trim()) params.district = district.trim();

  const response = await api.get<PublicDeliveryZoneResponse[]>('/api/v1/public/zones', { params });
  const data = response.data;

  if (!Array.isArray(data)) {
    throw new Error('Invalid response structure received from delivery zone service.');
  }

  return data.map((z) => ({
    id: String(z.id),
    zoneCode: z.zoneCode,
    zoneName: z.zoneName,
    district: z.district,
  }));
}
