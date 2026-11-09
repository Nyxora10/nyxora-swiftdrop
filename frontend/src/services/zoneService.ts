import { DeliveryZone } from '@/types/booking';
import { DEFAULT_FALLBACK_ZONES } from '@/constants/booking';

interface DeliveryZoneApiRecord {
  id?: string | number;
  zoneId?: string | number;
  uuid?: string | number;
  zoneCode?: string;
  zoneName?: string;
  name?: string;
  district?: string;
  baseSurcharge?: number;
}

/**
 * Service to fetch active logistics delivery zones across Sri Lanka.
 */
export async function getDeliveryZones(): Promise<DeliveryZone[]> {
  try {
    const res = await fetch('/api/v1/zones');
    if (!res.ok) {
      console.warn(`Zones fetch failed with status ${res.status}. Utilizing resilient fallback zones.`);
      return DEFAULT_FALLBACK_ZONES;
    }

    const resData: unknown = await res.json();
    const fetched = resData && typeof resData === 'object' && 'content' in resData
      ? (resData as { content?: unknown }).content
      : resData;

    if (Array.isArray(fetched) && fetched.length > 0) {
      return fetched.map((zone: unknown) => {
        const record = zone as DeliveryZoneApiRecord;
        return {
          id: String(record.id || record.zoneId || record.uuid),
          zoneCode: record.zoneCode || 'ZN',
          zoneName: record.zoneName || record.name || 'Delivery Zone',
          district: record.district || 'District',
          baseSurcharge: record.baseSurcharge || 0,
        };
      });
    }

    return DEFAULT_FALLBACK_ZONES;
  } catch (error) {
    console.warn('Network error while retrieving zones. Utilizing fallback delivery zones:', error);
    return DEFAULT_FALLBACK_ZONES;
  }
}

