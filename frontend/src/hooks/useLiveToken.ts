import { useCallback, useEffect, useRef, useState } from 'react';
import { tokenApi } from '../services/api';
import { HttpError } from '../services/http';
import { subscribeTopic, tokenTopic, type ConnectionState } from '../services/realtime';
import type { TokenEvent, TokenView } from '../types/api';

/**
 * Live view of one token. Combines a REST load (source of truth) with a STOMP subscription.
 * On every (re)connect it refetches, so nothing missed while offline is lost. If the socket stays
 * down it falls back to polling.
 */
export function useLiveToken(reference: string, onEvent?: (e: TokenEvent) => void) {
  const [token, setToken] = useState<TokenView | null>(null);
  const [error, setError] = useState<HttpError | null>(null);
  const [loading, setLoading] = useState(true);
  const [connection, setConnection] = useState<ConnectionState>('connecting');
  const onEventRef = useRef(onEvent);
  onEventRef.current = onEvent;

  const refresh = useCallback(async () => {
    try {
      setToken(await tokenApi.byReference(reference));
      setError(null);
    } catch (e) {
      setError(e instanceof HttpError ? e : new HttpError(0, null));
    } finally {
      setLoading(false);
    }
  }, [reference]);

  useEffect(() => {
    setLoading(true);
    void refresh();
    return subscribeTopic<TokenEvent>(tokenTopic(reference), {
      onMessage: (event) => {
        setToken(event.token);
        onEventRef.current?.(event);
      },
      onState: setConnection,
      onConnect: () => void refresh(),
    });
  }, [reference, refresh]);

  useEffect(() => {
    if (connection === 'online') return;
    const id = window.setInterval(() => void refresh(), 8000);
    return () => window.clearInterval(id);
  }, [connection, refresh]);

  return { token, setToken, error, loading, connection, refresh };
}
