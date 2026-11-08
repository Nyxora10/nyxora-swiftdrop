import { NextResponse } from 'next/server';

const SEED_ZONES = [
  { id: 1, zoneCode: 'WP-COL-01', zoneName: 'Colombo Central Metro', district: 'Colombo', baseSurcharge: 0, isActive: true },
  { id: 2, zoneCode: 'WP-GAM-01', zoneName: 'Gampaha Industrial Hub', district: 'Gampaha', baseSurcharge: 25, isActive: true },
  { id: 3, zoneCode: 'CP-KAN-01', zoneName: 'Kandy Urban District', district: 'Kandy', baseSurcharge: 50, isActive: true },
  { id: 4, zoneCode: 'SP-GAL-01', zoneName: 'Galle Coastal Belt', district: 'Galle', baseSurcharge: 40, isActive: true },
];

const SEED_RULES = [
  { id: 1, originZone: SEED_ZONES[0], destinationZone: SEED_ZONES[2], baseFee: 450, ratePerKg: 120, parcelSize: 'MEDIUM', sizeMultiplier: 1.25 },
  { id: 2, originZone: SEED_ZONES[0], destinationZone: SEED_ZONES[1], baseFee: 250, ratePerKg: 80, parcelSize: 'SMALL', sizeMultiplier: 1.0 },
  { id: 3, originZone: SEED_ZONES[0], destinationZone: SEED_ZONES[3], baseFee: 550, ratePerKg: 140, parcelSize: 'LARGE', sizeMultiplier: 1.5 },
];

export async function GET(request: Request) {
  try {
    const backendRes = await fetch('http://localhost:8080/api/v1/pricing/rules', {
      headers: {
        'Authorization': request.headers.get('authorization') || '',
        'X-User-Role': request.headers.get('x-user-role') || 'ROLE_ADMIN',
      },
      signal: AbortSignal.timeout(600),
    });
    if (backendRes.ok) {
      return NextResponse.json(await backendRes.json());
    }
  } catch {
    // Backend offline -> serve seed rules cleanly
  }

  return NextResponse.json(SEED_RULES);
}

