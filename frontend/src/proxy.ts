import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

// Route configuration mapping prefixes to authorized roles
const ROLE_ROUTE_CONFIG: Record<string, string[]> = {
  '/customer': ['ROLE_CUSTOMER', 'ROLE_ADMIN'],
  '/merchant': ['ROLE_MERCHANT', 'ROLE_ADMIN'],
  '/courier': ['ROLE_COURIER', 'ROLE_ADMIN'],
  '/admin': ['ROLE_ADMIN'],
};

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;

  const token = request.cookies.get('swiftdrop_access_token')?.value;
  const role = request.cookies.get('swiftdrop_role')?.value;
  const status = request.cookies.get('swiftdrop_status')?.value;

  // 1. Unauthenticated users attempting to access protected routes
  const isProtectedPath = Object.keys(ROLE_ROUTE_CONFIG).some((prefix) =>
    pathname.startsWith(prefix)
  );

  const isHoldingScreen =
    pathname === '/pending-approval' ||
    pathname === '/rejected' ||
    pathname === '/suspended';

  if (!token && (isProtectedPath || isHoldingScreen)) {
    const loginUrl = new URL('/login', request.url);
    loginUrl.searchParams.set('redirect', pathname);
    return NextResponse.redirect(loginUrl);
  }

  // If token exists, enforce lifecycle state locks
  if (token && isProtectedPath) {
    if (status === 'PENDING_APPROVAL' && pathname !== '/pending-approval') {
      return NextResponse.redirect(new URL('/pending-approval', request.url));
    }
    if (status === 'REJECTED' && pathname !== '/rejected') {
      return NextResponse.redirect(new URL('/rejected', request.url));
    }
    if (status === 'SUSPENDED' && pathname !== '/suspended') {
      return NextResponse.redirect(new URL('/suspended', request.url));
    }
  }

  // 2. Enforce Role-Based Access Control (RBAC) boundaries
  for (const [prefix, allowedRoles] of Object.entries(ROLE_ROUTE_CONFIG)) {
    if (pathname.startsWith(prefix)) {
      if (!role || !allowedRoles.includes(role)) {
        // Redirect to appropriate role home if available, otherwise forbidden view
        if (role === 'ROLE_CUSTOMER') {
          return NextResponse.redirect(new URL('/customer/book', request.url));
        }
        if (role === 'ROLE_MERCHANT') {
          return NextResponse.redirect(new URL('/merchant/dashboard', request.url));
        }
        if (role === 'ROLE_COURIER') {
          return NextResponse.redirect(new URL('/courier/runsheet', request.url));
        }
        if (role === 'ROLE_ADMIN') {
          return NextResponse.redirect(new URL('/admin/dispatch', request.url));
        }
        return NextResponse.redirect(new URL('/unauthorized', request.url));
      }
    }
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    '/customer/:path*',
    '/merchant/:path*',
    '/courier/:path*',
    '/admin/:path*',
    '/pending-approval',
    '/rejected',
    '/suspended',
  ],
};

