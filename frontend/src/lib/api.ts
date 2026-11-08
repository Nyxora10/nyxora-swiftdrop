import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { authStorage } from './auth';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 15000,
});

// Refresh token state to queue concurrent failed requests
let isRefreshing = false;
let failedQueue: Array<{
  resolve: (token: string) => void;
  reject: (error: unknown) => void;
}> = [];

const processQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach((promise) => {
    if (error) {
      promise.reject(error);
    } else if (token) {
      promise.resolve(token);
    }
  });
  failedQueue = [];
};

/**
 * Request Interceptor:
 * Injects Authorization header with Bearer token if user is authenticated.
 */
api.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = authStorage.getAccessToken();
    if (token && !config.headers.Authorization) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

/**
 * Response Interceptor:
 * Catches HTTP 401 Unauthorized errors, queues requests, calls /api/v1/auth/refresh,
 * updates stored tokens, and transparently replays all failed requests.
 */
api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

    // If error is not 401 or request was already retried, propagate error
    if (!error.response || error.response.status !== 401 || originalRequest._retry) {
      return Promise.reject(error);
    }

    // Do not attempt refresh on auth endpoints (login, refresh, register)
    if (
      originalRequest.url?.includes('/api/v1/auth/login') ||
      originalRequest.url?.includes('/api/v1/auth/refresh') ||
      originalRequest.url?.includes('/api/v1/auth/register')
    ) {
      return Promise.reject(error);
    }

    // Queue request if token refresh is already ongoing
    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        failedQueue.push({ resolve, reject });
      })
        .then((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          return api(originalRequest);
        })
        .catch((err) => Promise.reject(err));
    }

    originalRequest._retry = true;
    isRefreshing = true;

    const refreshToken = authStorage.getRefreshToken();
    if (!refreshToken) {
      isRefreshing = false;
      authStorage.clearSession();
      if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
        const loginUrl = new URL('/login?session_expired=true', window.location.origin).toString();
        window.location.replace(loginUrl);
      }
      return Promise.reject(error);
    }

    try {
      // Direct raw axios call bypassing interceptors to avoid circular refresh loops
      const response = await axios.post(`${API_BASE_URL}/api/v1/auth/refresh`, {
        refreshToken,
      });

      const { accessToken: newAccessToken, refreshToken: newRefreshToken } = response.data;

      authStorage.setAccessToken(newAccessToken);
      if (newRefreshToken) {
        authStorage.setRefreshToken(newRefreshToken);
      }

      originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
      processQueue(null, newAccessToken);

      return api(originalRequest);
    } catch (refreshError) {
      processQueue(refreshError, null);
      authStorage.clearSession();
      if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
        const loginUrl = new URL('/login?session_expired=true', window.location.origin).toString();
        window.location.replace(loginUrl);
      }
      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  }
);

export default api;

