import { useEffect, useRef, useState } from 'react';
import { queueTopic, subscribeTopic, type ConnectionState } from '../services/realtime';
import type { QueueEvent } from '../types/api';

/**
 * Subscribes to a queue's public topic and calls `onEvent` for each change.
 * `onResync` runs on every (re)connect and while the socket is down (polling fallback).
 */
export function useQueueEvents(
  publicRef: string | undefined,
  onEvent: (e: QueueEvent) => void,
  onResync?: () => void,
): ConnectionState {
  const [connection, setConnection] = useState<ConnectionState>('connecting');
  const eventRef = useRef(onEvent);
  const resyncRef = useRef(onResync);
  eventRef.current = onEvent;
  resyncRef.current = onResync;

  useEffect(() => {
    if (!publicRef) return;
    return subscribeTopic<QueueEvent>(queueTopic(publicRef), {
      onMessage: (e) => eventRef.current(e),
      onState: setConnection,
      onConnect: () => resyncRef.current?.(),
    });
  }, [publicRef]);

  useEffect(() => {
    if (connection === 'online' || !publicRef) return;
    const id = window.setInterval(() => resyncRef.current?.(), 8000);
    return () => window.clearInterval(id);
  }, [connection, publicRef]);

  return connection;
}
