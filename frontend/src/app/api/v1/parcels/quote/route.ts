import { NextResponse } from 'next/server';

export async function POST(request: Request) {
  try {
    const backendRes = await fetch('http://localhost:8080/api/v1/parcels/quote', {
      method: 'POST',
      headers: {
        'Authorization': request.headers.get('authorization') || '',
        'Content-Type': 'application/json',
      },
      body: await request.text(),
      signal: AbortSignal.timeout(5000),
    });

    return new NextResponse(await backendRes.text(), {
      status: backendRes.status,
      headers: { 'Content-Type': backendRes.headers.get('content-type') || 'application/json' },
    });
  } catch {
    return NextResponse.json(
      { title: 'Parcel service unavailable', detail: 'A live quote could not be calculated because the parcel service is unavailable.' },
      { status: 503 }
    );
  }
}
