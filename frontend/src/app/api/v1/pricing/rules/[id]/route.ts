import { NextResponse } from 'next/server';

type RouteContext = { params: Promise<{ id: string }> };

export async function PUT(request: Request, context: RouteContext) {
  const { id } = await context.params;
  try {
    const backendRes = await fetch(`http://localhost:8080/api/v1/pricing/rules/${encodeURIComponent(id)}`, {
      method: 'PUT',
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
      { title: 'Parcel service unavailable', detail: 'The pricing rule could not be updated because the parcel service is unavailable.' },
      { status: 503 }
    );
  }
}
