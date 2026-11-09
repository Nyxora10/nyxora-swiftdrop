import { DeliveryZone } from '@/types/booking';
import { DEFAULT_FALLBACK_ZONES } from '@/constants/booking';

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

    const resData = await res.json();
    const fetched = resData?.content || resData;

    if (Array.isArray(fetched) && fetched.length > 0) {
      return fetched.map((z: any) => ({
        id: String(z.id || z.zoneId || z.uuid),
        zoneCode: z.zoneCode || 'ZN',
        zoneName: z.zoneName || z.name || 'Delivery Zone',
        district: z.district || 'District',
        baseSurcharge: z.baseSurcharge || 0,
      }));
    }

    return DEFAULT_FALLBACK_ZONES;
  } catch (error) {
    console.warn('Network error while retrieving zones. Utilizing fallback delivery zones:', error);
    return DEFAULT_FALLBACK_ZONES;
  }
}

