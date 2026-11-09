import { NextResponse } from 'next/server';

export async function POST(
  request: Request,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  let reason = '';
  try {
    const body = await request.json();
    reason = typeof body.reason === 'string' ? body.reason : '';
  } catch {
    // Forward an empty payload so the backend returns its normal validation error.
  }

  try {
    const backendRes = await fetch(`http://localhost:8080/api/v1/auth/admin/users/${id}/reject`, {
      method: 'PUT',
      headers: {
        'Authorization': request.headers.get('authorization') || '',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ reason }),
      signal: AbortSignal.timeout(5000),
    });

    return new NextResponse(await backendRes.text(), {
      status: backendRes.status,
      headers: {
        'Content-Type': backendRes.headers.get('content-type') || 'application/json',
      },
    });
  } catch {
    return NextResponse.json(
      {
        title: 'Auth service unavailable',
        detail: 'The rejection could not be saved because the authentication service is unavailable.',
      },
      { status: 503 }
    );
  }
}

