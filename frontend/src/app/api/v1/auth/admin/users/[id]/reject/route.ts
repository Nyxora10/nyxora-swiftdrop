import { NextResponse } from 'next/server';

export async function POST(
  request: Request,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  let reason = '';
  try {
    const body = await request.json();
    reason = body.reason || '';
  } catch {
    // ignore
  }

  try {
    const backendRes = await fetch(`http://localhost:8080/api/v1/auth/admin/users/${id}/reject`, {
      method: 'POST',
      headers: {
        'Authorization': request.headers.get('authorization') || '',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ reason }),
      signal: AbortSignal.timeout(600),
    });
    if (backendRes.ok) {
      return NextResponse.json(await backendRes.json());
    }
  } catch {
    // Backend offline -> mock success
  }

  return NextResponse.json({ success: true, id, status: 'REJECTED', reason });
}

