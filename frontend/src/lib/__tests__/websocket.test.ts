import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  Client,
  type IFrame,
  type closeEventCallbackType,
  type frameCallbackType,
} from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useParcelWebSocket } from '@/lib/websocket';

vi.mock('sockjs-client', () => ({
  default: vi.fn(() => ({})),
}));

vi.mock('@stomp/stompjs', () => ({
  Client: class MockClient {
    static latest: MockClient;
    activate = vi.fn();
    deactivate = vi.fn().mockResolvedValue(undefined);
    subscribe = vi.fn();

    constructor(configuration: {
      onConnect?: frameCallbackType;
      onDisconnect?: frameCallbackType;
      onWebSocketClose?: closeEventCallbackType;
      onStompError?: frameCallbackType;
    }) {
      this.onConnect = configuration.onConnect;
      this.onDisconnect = configuration.onDisconnect;
      this.onWebSocketClose = configuration.onWebSocketClose;
      this.onStompError = configuration.onStompError;
      MockClient.latest = this;
    }

    onConnect?: frameCallbackType;
    onDisconnect?: frameCallbackType;
    onWebSocketClose?: closeEventCallbackType;
    onStompError?: frameCallbackType;
  },
}));

describe('useParcelWebSocket', () => {
  let client: {
    activate: ReturnType<typeof vi.fn>;
    deactivate: ReturnType<typeof vi.fn>;
    subscribe: ReturnType<typeof vi.fn>;
    onConnect?: frameCallbackType;
    onDisconnect?: frameCallbackType;
    onWebSocketClose?: closeEventCallbackType;
    onStompError?: frameCallbackType;
  };
  let subscription: { unsubscribe: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    subscription = { unsubscribe: vi.fn() };
    const MockClient = Client as unknown as {
      latest: typeof client;
    };
    MockClient.latest = undefined as unknown as typeof client;
  });

  it('connects and subscribes to the parcel topic', async () => {
    const onUpdate = vi.fn();
    const { result } = renderHook(() => useParcelWebSocket('SD-123', onUpdate));
    client = (Client as unknown as { latest: typeof client }).latest;
    client.subscribe.mockReturnValue(subscription);

    expect(result.current).toBe('RECONNECTING');
    expect(client.activate).toHaveBeenCalledOnce();
    expect(SockJS).toBeDefined();

    await act(async () => {
      client.onConnect?.({} as IFrame);
    });

    await waitFor(() => expect(result.current).toBe('CONNECTED'));
    expect(client.subscribe).toHaveBeenCalledWith(
      '/topic/parcels/SD-123',
      expect.any(Function)
    );
  });

  it('forwards valid updates and ignores invalid JSON', async () => {
    const onUpdate = vi.fn();
    const { unmount } = renderHook(() => useParcelWebSocket('SD-123', onUpdate));
    client = (Client as unknown as { latest: typeof client }).latest;
    client.subscribe.mockReturnValue(subscription);

    await act(async () => {
      client.onConnect?.({} as IFrame);
    });

    const messageHandler = client.subscribe.mock.calls[0][1] as (message: { body: string }) => void;
    const update = {
      trackingNumber: 'SD-123',
      currentStatus: 'IN_TRANSIT',
      timestamp: '2026-11-10T00:00:00Z',
    };

    messageHandler({ body: JSON.stringify(update) });
    expect(onUpdate).toHaveBeenCalledWith(update);

    expect(() => messageHandler({ body: '{invalid-json' })).not.toThrow();
    expect(onUpdate).toHaveBeenCalledOnce();

    unmount();
    expect(subscription.unsubscribe).toHaveBeenCalledOnce();
    expect(client.deactivate).toHaveBeenCalledOnce();
  });

  it('reports reconnecting when the socket closes', async () => {
    const { result } = renderHook(() => useParcelWebSocket('SD-123', vi.fn()));
    client = (Client as unknown as { latest: typeof client }).latest;
    client.subscribe.mockReturnValue(subscription);

    await act(async () => {
      client.onConnect?.({} as IFrame);
      client.onWebSocketClose?.(new CloseEvent('close'));
    });

    await waitFor(() => expect(result.current).toBe('RECONNECTING'));
  });
});
