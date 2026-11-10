'use client';

import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useEffect, useRef, useState } from 'react';
import type { ParcelTrackingUpdate } from '@/types/tracking';

export type ParcelWebSocketStatus = 'CONNECTED' | 'RECONNECTING' | 'DISCONNECTED';

const WEBSOCKET_ENDPOINT =
  process.env.NEXT_PUBLIC_WEBSOCKET_URL || 'http://localhost:8080/ws-swiftdrop';

export function useParcelWebSocket(
  trackingNumber: string,
  onUpdate: (update: ParcelTrackingUpdate) => void
): ParcelWebSocketStatus {
  const [status, setStatus] = useState<ParcelWebSocketStatus>(() =>
    trackingNumber.trim() ? 'RECONNECTING' : 'DISCONNECTED'
  );
  const onUpdateRef = useRef(onUpdate);

  useEffect(() => {
    onUpdateRef.current = onUpdate;
  }, [onUpdate]);

  useEffect(() => {
    const normalizedTrackingNumber = trackingNumber.trim();
    if (!normalizedTrackingNumber) {
      queueMicrotask(() => setStatus('DISCONNECTED'));
      return;
    }

    let subscription: StompSubscription | undefined;
    let isActive = true;

    const client = new Client({
      webSocketFactory: () => new SockJS(WEBSOCKET_ENDPOINT),
      reconnectDelay: 5000,
      onConnect: () => {
        if (!isActive) {
          return;
        }

        subscription = client.subscribe(
          `/topic/parcels/${encodeURIComponent(normalizedTrackingNumber)}`,
          (message: IMessage) => {
            try {
              const update = JSON.parse(message.body) as ParcelTrackingUpdate;
              onUpdateRef.current(update);
            } catch (error) {
              console.error('Unable to parse parcel tracking update.', error);
            }
          }
        );
        setStatus('CONNECTED');
      },
      onDisconnect: () => {
        if (isActive) {
          setStatus('DISCONNECTED');
        }
      },
      onWebSocketClose: () => {
        if (isActive) {
          setStatus('RECONNECTING');
        }
      },
      onStompError: () => {
        if (isActive) {
          setStatus('RECONNECTING');
        }
      },
    });

    client.activate();

    return () => {
      isActive = false;
      subscription?.unsubscribe();
      void client.deactivate();
    };
  }, [trackingNumber]);

  return status;
}
