import { NextResponse } from 'next/server';

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);

  try {
    const backendRes = await fetch(`http://localhost:8080/api/v1/auth/admin/users?${searchParams.toString()}`, {
      headers: {
        'Authorization': request.headers.get('authorization') || '',
        'Content-Type': 'application/json',
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
      { title: 'Auth service unavailable', detail: 'The partner queue is unavailable because the authentication service is offline.' },
      { status: 503, headers: { 'Cache-Control': 'no-store' } },
    );
  }
}

