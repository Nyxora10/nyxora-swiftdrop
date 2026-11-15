/**
 * SwiftDrop Courier Progressive Web App (PWA) Service Worker
 * Handles offline caching and network resiliency for mobile couriers.
 */

const CACHE_NAME = 'swiftdrop-courier-v1';

const STATIC_ASSETS = [
  '/',
  '/manifest.json',
  '/courier/runsheet',
  '/favicon.ico',
];

// Install Event: Pre-cache essential app shell assets
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      console.log('[PWA ServiceWorker] Pre-caching offline app shell');
      return cache.addAll(STATIC_ASSETS).catch((err) => {
        console.warn('[PWA ServiceWorker] Non-fatal pre-cache warning:', err);
      });
    })
  );
  self.skipWaiting();
});

// Activate Event: Clean up outdated caches
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keyList) => {
      return Promise.all(
        keyList.map((key) => {
          if (key !== CACHE_NAME) {
            console.log('[PWA ServiceWorker] Removing old cache:', key);
            return caches.delete(key);
          }
        })
      );
    })
  );
  self.clients.claim();
});

// Fetch Event: Network-first for API requests, Stale-while-revalidate for static assets
self.addEventListener('fetch', (event) => {
  const requestUrl = new URL(event.request.url);

  // Bypass non-GET requests (PATCH/POST/DELETE must always reach server)
  if (event.request.method !== 'GET') {
    return;
  }

  // Service workers can observe requests initiated by browser extensions. Cache
  // only same-origin HTTP(S) requests handled by this application.
  if (
    (requestUrl.protocol !== 'http:' && requestUrl.protocol !== 'https:') ||
    requestUrl.origin !== self.location.origin
  ) {
    return;
  }

  // Never cache Next.js development assets or webpack HMR requests.
  if (requestUrl.pathname.startsWith('/_next/') || requestUrl.pathname === '/sw.js') {
    return;
  }

  // Network-First for API requests to ensure fresh parcel data
  if (requestUrl.pathname.startsWith('/api/')) {
    event.respondWith(
      fetch(event.request).catch(() => {
        return caches.match(event.request);
      })
    );
    return;
  }

  // Stale-While-Revalidate for app pages & static resources
  event.respondWith(
    caches.match(event.request).then((cachedResponse) => {
      const fetchPromise = fetch(event.request).then((networkResponse) => {
        if (networkResponse && networkResponse.ok && networkResponse.type === 'basic') {
          const responseToCache = networkResponse.clone();
          event.waitUntil(
            caches.open(CACHE_NAME).then((cache) => {
              return cache.put(event.request, responseToCache);
            }).catch((err) => {
              console.warn('[PWA ServiceWorker] Failed to cache response:', err);
            })
          );
        }
        return networkResponse;
      }).catch(() => {
        return cachedResponse;
      });

      return cachedResponse || fetchPromise;
    })
  );
});

