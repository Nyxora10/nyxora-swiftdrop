import { NextResponse } from 'next/server';

export async function GET(request: Request) {
  try {
    const backendRes = await fetch('http://localhost:8080/api/v1/zones', {
      headers: {
        'Authorization': request.headers.get('authorization') || '',
      },
      signal: AbortSignal.timeout(5000),
    });

    return new NextResponse(await backendRes.text(), {
      status: backendRes.status,
      headers: {
        'Content-Type': backendRes.headers.get('content-type') || 'application/json',
        'X-SwiftDrop-Data-Source': 'backend',
      },
    });
  } catch {
    return NextResponse.json(
      { title: 'Parcel service unavailable', detail: 'Delivery zones are unavailable because the parcel service is offline.' },
      { status: 503, headers: { 'Cache-Control': 'no-store' } },
    );
  }
}

export async function POST(request: Request) {
  try {
    const backendRes = await fetch('http://localhost:8080/api/v1/zones', {
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
      { title: 'Parcel service unavailable', detail: 'The delivery zone could not be saved because the parcel service is unavailable.' },
      { status: 503 }
    );
  }
}

