import { Client, type IMessage } from '@stomp/stompjs';
import { API_URL } from './http';

export type ConnectionState = 'connecting' | 'online' | 'offline';

function brokerUrl(): string {
  if (API_URL) return API_URL.replace(/^http/, 'ws') + '/ws';
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${proto}://${window.location.host}/ws`;
}

/**
 * Subscribes to one STOMP topic with automatic, back-off reconnects.
 * `onConnect` fires on every (re)connection so callers can refetch anything they missed while offline.
 */
export function subscribeTopic<T>(
  destination: string,
  handlers: {
    onMessage: (payload: T) => void;
    onState?: (state: ConnectionState) => void;
    onConnect?: () => void;
  },
): () => void {
  let attempts = 0;
  const client = new Client({
    brokerURL: brokerUrl(),
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    reconnectDelay: 1_000,
    debug: () => undefined,
  });

  client.beforeConnect = async () => {
    handlers.onState?.('connecting');
    // Exponential back-off (1s, 2s, 4s ... capped at 15s) between reconnect attempts
    client.reconnectDelay = Math.min(1_000 * 2 ** attempts, 15_000);
    attempts += 1;
  };

  client.onConnect = () => {
    attempts = 0;
    client.reconnectDelay = 1_000;
    handlers.onState?.('online');
    client.subscribe(destination, (msg: IMessage) => {
      try {
        handlers.onMessage(JSON.parse(msg.body) as T);
      } catch {
        /* ignore malformed frame */
      }
    });
    handlers.onConnect?.();
  };
  client.onWebSocketClose = () => handlers.onState?.('offline');
  client.onStompError = () => handlers.onState?.('offline');

  client.activate();
  return () => {
    void client.deactivate();
  };
}

export const queueTopic = (publicRef: string) => `/topic/queue/${publicRef}`;
export const tokenTopic = (reference: string) => `/topic/token/${reference}`;
