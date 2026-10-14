import type { TokenStatus } from '../types/api';

export function formatTime(iso: string | null | undefined): string {
  if (!iso) return '-';
  return new Date(iso).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '-';
  return new Date(iso).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
}

export function formatWait(minutes: number | null | undefined): string {
  if (minutes === null || minutes === undefined) return '-';
  if (minutes <= 0) return 'Under a minute';
  if (minutes < 60) return `~${minutes} min`;
  const h = Math.floor(minutes / 60);
  const m = Math.round(minutes % 60);
  return m === 0 ? `~${h} hr` : `~${h} hr ${m} min`;
}

export function formatSeconds(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return m > 0 ? `${m}m ${String(s).padStart(2, '0')}s` : `${s}s`;
}

export const statusLabel: Record<TokenStatus, string> = {
  WAITING: 'Waiting',
  CALLED: 'Called',
  SERVING: 'Being served',
  COMPLETED: 'Completed',
  SKIPPED: 'Skipped',
  CANCELLED: 'Cancelled',
  NO_SHOW: 'No-show',
};

export const reasonText: Record<string, string> = {
  QUEUE_PAUSED: 'This queue is paused right now.',
  QUEUE_CLOSED: 'This queue is closed.',
  OUTSIDE_WORKING_HOURS: 'This queue is outside its working hours.',
  QUEUE_ON_BREAK: 'This queue is on a break. Try again shortly.',
  QUEUE_FULL: 'This queue is full. Try again later.',
};

/** Remembers the references of tokens issued on this device so guests can find them again. */
const LOCAL_KEY = 'sq.tokens';
export function rememberToken(reference: string, label: string) {
  try {
    const list = recentTokens().filter((t) => t.reference !== reference);
    list.unshift({ reference, label, at: Date.now() });
    localStorage.setItem(LOCAL_KEY, JSON.stringify(list.slice(0, 10)));
  } catch {
    /* storage unavailable */
  }
}
export function recentTokens(): { reference: string; label: string; at: number }[] {
  try {
    return JSON.parse(localStorage.getItem(LOCAL_KEY) ?? '[]');
  } catch {
    return [];
  }
}

/** Accepts a bare reference or a full /queue/token/{reference} link. */
export function extractReference(input: string): string {
  const trimmed = input.trim();
  const match = trimmed.match(/\/queue\/token\/([A-Za-z0-9_-]+)/);
  return match ? match[1] : trimmed;
}

/** Accepts a bare public ref or a full /join/{ref} link (from a scanned QR code). */
export function extractQueueRef(input: string): string | null {
  const trimmed = input.trim();
  const match = trimmed.match(/\/join\/([A-Za-z0-9_-]{6,64})/);
  if (match) return match[1];
  return /^[A-Za-z0-9_-]{6,64}$/.test(trimmed) ? trimmed : null;
}
