import { NextResponse } from 'next/server';

export async function GET(
  request: Request,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;

  try {
    const backendRes = await fetch(
      `http://localhost:8080/api/v1/auth/admin/users/${encodeURIComponent(id)}`,
      {
        headers: {
          'Authorization': request.headers.get('authorization') || '',
          'Content-Type': 'application/json',
        },
        signal: AbortSignal.timeout(5000),
      }
    );

    return new NextResponse(await backendRes.text(), {
      status: backendRes.status,
      headers: {
        'Content-Type': backendRes.headers.get('content-type') || 'application/json',
        'Cache-Control': 'no-store',
      },
    });
  } catch {
    return NextResponse.json(
      {
        title: 'Auth service unavailable',
        detail: 'Partner details could not be loaded because the authentication service is unavailable.',
      },
      { status: 503, headers: { 'Cache-Control': 'no-store' } }
    );
  }
}
