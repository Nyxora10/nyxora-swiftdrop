import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import { ToastProvider } from "@/components/layout/ToastContext";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "SwiftDrop | Courier & Parcel Management System",
  description: "Cloud-native parcel delivery, tracking, and courier dispatching platform.",
  manifest: "/manifest.json",
};

const isProduction = process.env.NODE_ENV === "production";

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="en"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col text-slate-900 bg-slate-50">
        <ToastProvider>{children}</ToastProvider>

        <script
          dangerouslySetInnerHTML={{
            __html: isProduction
              ? `
                if ('serviceWorker' in navigator) {
                  window.addEventListener('load', function() {
                    navigator.serviceWorker.register('/sw.js', { updateViaCache: 'none' }).then(
                      function(reg) {
                        console.log('[PWA] ServiceWorker registration successful with scope: ', reg.scope);
                      },
                      function(err) {
                        console.warn('[PWA] ServiceWorker registration failed: ', err);
                      }
                    );
                  });
                }
              `
              : `
                if ('serviceWorker' in navigator) {
                  window.addEventListener('load', function() {
                    navigator.serviceWorker.getRegistrations().then(function(registrations) {
                      return Promise.all(registrations
                        .filter(function(reg) {
                          return [reg.active, reg.waiting, reg.installing].some(function(worker) {
                            return worker && new URL(worker.scriptURL).pathname === '/sw.js';
                          });
                        })
                        .map(function(reg) {
                          return reg.unregister();
                        }));
                    }).then(function() {
                      return caches.keys().then(function(cacheNames) {
                        return Promise.all(cacheNames
                          .filter(function(cacheName) {
                            return cacheName.startsWith('swiftdrop-');
                          })
                          .map(function(cacheName) {
                            return caches.delete(cacheName);
                          }));
                      });
                    }).catch(function(err) {
                      console.warn('[PWA] Development service worker cleanup failed: ', err);
                    });
                  });
                }
              `,
          }}
        />
      </body>
    </html>
  );
}
