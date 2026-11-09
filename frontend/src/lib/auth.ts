/**
 * SwiftDrop Authentication & Session Helper
 * Manages Access & Refresh Tokens in memory, LocalStorage, and Cookies
 * to ensure synchronization with Next.js Edge Middleware.
 */

import { useSyncExternalStore } from 'react';

export interface UserSession {
  userId: string;
  email: string;
  role: 'ROLE_CUSTOMER' | 'ROLE_MERCHANT' | 'ROLE_COURIER' | 'ROLE_ADMIN' | string;
  fullName: string;
  status?: 'ACTIVE' | 'PENDING_APPROVAL' | 'REJECTED' | 'SUSPENDED';
  statusReason?: string;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  expiresInMs?: number;
}

const ACCESS_TOKEN_KEY = 'swiftdrop_access_token';
const REFRESH_TOKEN_KEY = 'swiftdrop_refresh_token';
const USER_KEY = 'swiftdrop_user';

function isJwtFormatted(token: string): boolean {
  const segments = token.split('.');
  return segments.length === 3 && segments.every((segment) => segment.length > 0);
}

// Cookie helper
function setCookie(name: string, value: string, days = 7) {
  if (typeof document === 'undefined') return;
  const expires = new Date(Date.now() + days * 864e5).toUTCString();
  document.cookie = `${name}=${encodeURIComponent(value)}; expires=${expires}; path=/; SameSite=Lax`;
}

function getCookie(name: string): string | null {
  if (typeof document === 'undefined') return null;
  const match = document.cookie.match(new RegExp('(^|;\\s*)(' + name + ')=([^;]*)'));
  return match ? decodeURIComponent(match[3]) : null;
}

function removeCookie(name: string) {
  if (typeof document === 'undefined') return;
  document.cookie = `${name}=; Max-Age=0; path=/; SameSite=Lax`;
}

export const authStorage = {
  getAccessToken(): string | null {
    if (typeof window === 'undefined') return null;
    const token = localStorage.getItem(ACCESS_TOKEN_KEY) || getCookie(ACCESS_TOKEN_KEY);
    if (token && !isJwtFormatted(token)) {
      this.clearSession();
      return null;
    }
    return token;
  },

  setAccessToken(token: string) {
    if (typeof window === 'undefined') return;
    localStorage.setItem(ACCESS_TOKEN_KEY, token);
    setCookie(ACCESS_TOKEN_KEY, token, 7);
    window.dispatchEvent(new Event('swiftdrop_auth_change'));
  },

  getRefreshToken(): string | null {
    if (typeof window === 'undefined') return null;
    return localStorage.getItem(REFRESH_TOKEN_KEY) || getCookie(REFRESH_TOKEN_KEY);
  },

  setRefreshToken(token: string) {
    if (typeof window === 'undefined') return;
    localStorage.setItem(REFRESH_TOKEN_KEY, token);
    setCookie(REFRESH_TOKEN_KEY, token, 14);
  },

  getUser(): UserSession | null {
    if (typeof window === 'undefined') return null;
    const raw = localStorage.getItem(USER_KEY);
    if (raw) {
      try {
        return JSON.parse(raw);
      } catch {
        return null;
      }
    }
    const roleCookie = getCookie('swiftdrop_role');
    const emailCookie = getCookie('swiftdrop_email');
    const userIdCookie = getCookie('swiftdrop_user_id');
    const nameCookie = getCookie('swiftdrop_name');
    const statusCookie = getCookie('swiftdrop_status');
    const statusReasonCookie = getCookie('swiftdrop_status_reason');

    if (roleCookie && emailCookie && userIdCookie) {
      return {
        userId: userIdCookie,
        email: emailCookie,
        role: roleCookie,
        fullName: nameCookie || '',
        status: (statusCookie as UserSession['status']) || 'ACTIVE',
        statusReason: statusReasonCookie || undefined,
      };
    }
    return null;
  },

  setUser(user: UserSession) {
    if (typeof window === 'undefined') return;
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    setCookie('swiftdrop_role', user.role, 7);
    setCookie('swiftdrop_email', user.email, 7);
    setCookie('swiftdrop_user_id', user.userId, 7);
    setCookie('swiftdrop_name', user.fullName || '', 7);
    setCookie('swiftdrop_status', user.status || 'ACTIVE', 7);
    if (user.statusReason) {
      setCookie('swiftdrop_status_reason', user.statusReason, 7);
    } else {
      removeCookie('swiftdrop_status_reason');
    }
    window.dispatchEvent(new Event('swiftdrop_auth_change'));
  },

  setSession(tokens: AuthTokens, user: UserSession) {
    this.setAccessToken(tokens.accessToken);
    this.setRefreshToken(tokens.refreshToken);
    this.setUser(user);
  },

  clearSession() {
    if (typeof window === 'undefined') return;
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(USER_KEY);

    removeCookie(ACCESS_TOKEN_KEY);
    removeCookie(REFRESH_TOKEN_KEY);
    removeCookie('swiftdrop_role');
    removeCookie('swiftdrop_email');
    removeCookie('swiftdrop_user_id');
    removeCookie('swiftdrop_name');
    removeCookie('swiftdrop_status');
    removeCookie('swiftdrop_status_reason');
    window.dispatchEvent(new Event('swiftdrop_auth_change'));
  },

  isAuthenticated(): boolean {
    return !!this.getAccessToken();
  },

  hasRole(expectedRole: string): boolean {
    const user = this.getUser();
    return user?.role === expectedRole;
  },
};

/**
 * React 19 / Next.js Hook to subscribe to user auth state without cascading renders
 */
function authSubscribe(callback: () => void) {
  if (typeof window === 'undefined') return () => {};
  window.addEventListener('storage', callback);
  window.addEventListener('swiftdrop_auth_change', callback);
  return () => {
    window.removeEventListener('storage', callback);
    window.removeEventListener('swiftdrop_auth_change', callback);
  };
}

function authSnapshot(): string {
  if (typeof window === 'undefined') return '';
  return localStorage.getItem(USER_KEY) || '';
}

function authServerSnapshot(): string {
  return '';
}

export function useUserSession(): UserSession | null {
  const raw = useSyncExternalStore(authSubscribe, authSnapshot, authServerSnapshot);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}
