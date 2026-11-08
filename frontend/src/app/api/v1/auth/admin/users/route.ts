import { NextResponse } from 'next/server';

interface Partner {
  id: string;
  email: string;
  fullName: string;
  phoneNumber?: string;
  role: 'ROLE_MERCHANT' | 'ROLE_COURIER';
  status: 'ACTIVE' | 'PENDING_APPROVAL' | 'SUSPENDED' | 'REJECTED';
  statusReason?: string;
  createdAt: string;
  businessName?: string;
  registrationNumber?: string;
  vehicleType?: string;
  licensePlate?: string;
}

const SEED_PARTNERS: Partner[] = [
  {
    id: 'p1',
    fullName: 'Kasun Bandara',
    email: 'kasun.courier@swiftdrop.lk',
    role: 'ROLE_COURIER',
    vehicleType: 'TVS Apache 160',
    licensePlate: 'WP BBD-4819',
    phoneNumber: '+94 77 123 4567',
    createdAt: '2026-11-07',
    status: 'PENDING_APPROVAL',
  },
  {
    id: 'p2',
    fullName: 'Ceylon Craft Artisans Ltd',
    email: 'ops@ceyloncraft.lk',
    role: 'ROLE_MERCHANT',
    businessName: 'Ceylon Craft Artisans',
    registrationNumber: 'PV-89241',
    phoneNumber: '+94 11 234 5678',
    createdAt: '2026-11-07',
    status: 'PENDING_APPROVAL',
  },
  {
    id: 'p3',
    fullName: 'Dilshan Silva',
    email: 'dilshan.fast@gmail.com',
    role: 'ROLE_COURIER',
    vehicleType: 'Bajaj Pulsar 150',
    licensePlate: 'CP BCK-9011',
    phoneNumber: '+94 71 987 6543',
    createdAt: '2026-11-06',
    status: 'PENDING_APPROVAL',
  },
  {
    id: 'p4',
    fullName: 'Colombo Agro Hub',
    email: 'contact@colomboagro.com',
    role: 'ROLE_MERCHANT',
    businessName: 'Colombo Agro Hub Pvt Ltd',
    registrationNumber: 'PV-100234',
    phoneNumber: '+94 11 456 7890',
    createdAt: '2026-11-04',
    status: 'ACTIVE',
  },
  {
    id: 'p5',
    fullName: 'Ravi Fernando',
    email: 'ravi.rider@gmail.com',
    role: 'ROLE_COURIER',
    vehicleType: 'Honda Dio',
    licensePlate: 'WP BGT-1122',
    phoneNumber: '+94 76 543 2109',
    createdAt: '2026-10-28',
    status: 'SUSPENDED',
    statusReason: 'Consecutive delivery cancellations exceeding SLA threshold without prior notice.',
  },
  {
    id: 'p6',
    fullName: 'Fake Merchant Co',
    email: 'scam@fakemail.com',
    role: 'ROLE_MERCHANT',
    businessName: 'Fake Merchant Co',
    registrationNumber: 'INV-0000',
    createdAt: '2026-10-25',
    status: 'REJECTED',
    statusReason: 'Invalid Sri Lankan Department of Registrar of Companies (ROC) certificate.',
  },
];

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);

  try {
    const backendRes = await fetch(`http://localhost:8080/api/v1/auth/admin/users?${searchParams.toString()}`, {
      headers: {
        'Authorization': request.headers.get('authorization') || '',
        'Content-Type': 'application/json',
      },
      signal: AbortSignal.timeout(600),
    });
    if (backendRes.ok) {
      return NextResponse.json(await backendRes.json());
    }
  } catch {
    // Backend offline -> serve seed partners cleanly
  }

  return NextResponse.json(SEED_PARTNERS);
}

