import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { ServingBoard } from '../components/ServingBoard';
import { Ticket } from '../components/Ticket';
import { Button, Card, ConnectionDot, ErrorNotice, QueueBadge, Skeleton, StatusBadge } from '../components/ui';
import { errorMessage } from '../hooks/useApi';
import { useLiveToken } from '../hooks/useLiveToken';
import { useToast } from '../hooks/useToast';
import { tokenApi } from '../services/api';
import { formatDateTime, formatTime, formatWait } from '../utils/format';
import type { TokenEvent, TokenView } from '../types/api';

type NotifyState = 'unsupported' | 'default' | 'granted' | 'denied';

function notifyState(): NotifyState {
  if (typeof Notification === 'undefined') return 'unsupported';
  return Notification.permission;
}

function showBrowserNotification(title: string, body: string) {
  try {
    if (typeof Notification !== 'undefined' && Notification.permission === 'granted') {
      new Notification(title, { body, tag: 'smartqueue-token', icon: '/favicon.svg' });
    }
    navigator.vibrate?.([200, 100, 200]);
  } catch {
    /* notifications are best-effort */
  }
}

export default function TokenPage() {
  const { reference = '' } = useParams();
  const toast = useToast();
  const [notify, setNotify] = useState<NotifyState>(notifyState());
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const startingAhead = useRef(0);
  const nearAnnounced = useRef(false);

  const onEvent = useCallback(
    (e: TokenEvent) => {
      if (e.type === 'CALLED' || e.type === 'RECALLED') {
        showBrowserNotification('It is your turn', e.message);
        toast.info(e.message);
      }
    },
    [toast],
  );
  const { token, setToken, error, loading, connection, refresh } = useLiveToken(reference, onEvent);

  // Track the starting position so the progress bar has a fixed scale
  if (token && token.status === 'WAITING') {
    startingAhead.current = Math.max(startingAhead.current, token.peopleAhead);
  }

  useEffect(() => {
    if (token?.status === 'WAITING' && token.nearTurn && !nearAnnounced.current) {
      nearAnnounced.current = true;
      showBrowserNotification('Your turn is coming up', 'Please head to the service area.');
    }
  }, [token?.status, token?.nearTurn]);

  useEffect(() => {
    if (token) document.title = `${token.tokenNumber} · SmartQueue`;
    return () => {
      document.title = 'SmartQueue';
    };
  }, [token?.tokenNumber]); // eslint-disable-line react-hooks/exhaustive-deps

  async function enableNotifications() {
    if (typeof Notification === 'undefined') return;
    setNotify((await Notification.requestPermission()) as NotifyState);
  }

  async function cancel() {
    setCancelling(true);
    try {
      setToken(await tokenApi.cancel(reference));
      toast.success('Your token was cancelled.');
      setConfirmOpen(false);
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setCancelling(false);
    }
  }

  if (loading) {
    return (
      <div className="mx-auto max-w-md px-4 py-8">
        <Skeleton className="h-72" />
        <Skeleton className="mt-4 h-24" />
      </div>
    );
  }
  if (error || !token) {
    return (
      <div className="mx-auto max-w-md px-4 py-10">
        <h1 className="mb-4 text-4xl">{error?.code === 'TOKEN_NOT_FOUND' ? 'Token not found' : 'Could not load your token'}</h1>
        <ErrorNotice
          message={error?.code === 'TOKEN_NOT_FOUND' ? 'Check the link, or look up your token from the My token page.' : errorMessage(error)}
          onRetry={error?.code === 'TOKEN_NOT_FOUND' ? undefined : () => void refresh()}
        />
        <Link to="/token" className="mt-4 inline-block font-semibold underline underline-offset-4">
          Go to My token
        </Link>
      </div>
    );
  }

  const called = token.status === 'CALLED' || token.status === 'SERVING';
  const waiting = token.status === 'WAITING';
  const finished = !waiting && !called;
  const progress = waiting ? progressPercent(token, startingAhead.current) : called || token.status === 'COMPLETED' ? 100 : 0;

  return (
    <div className="mx-auto max-w-md px-4 py-6 sm:py-10">
      {called && <CallBanner token={token} />}

      <Ticket
        tokenNumber={token.tokenNumber}
        caption="Your token"
        priority={token.priority}
        tone={called ? 'called' : finished ? 'done' : 'waiting'}
        stub={
          <div className="grid gap-3 text-left">
            <div className="flex items-center justify-between">
              <StatusBadge status={token.status} />
              <ConnectionDot state={connection} />
            </div>
            {waiting && (
              <dl className="grid grid-cols-2 gap-3">
                <div>
                  <dt className="text-sm text-ink-500">People ahead</dt>
                  <dd className="font-display text-4xl font-bold">{token.peopleAhead}</dd>
                </div>
                <div>
                  <dt className="text-sm text-ink-500">Estimated wait</dt>
                  <dd className="font-display text-4xl font-bold">{formatWait(token.estimatedWaitMinutes)}</dd>
                </div>
              </dl>
            )}
            {waiting && (
              <div>
                <div
                  className="h-2.5 overflow-hidden rounded-full bg-paper-200"
                  role="progressbar"
                  aria-valuemin={0}
                  aria-valuemax={100}
                  aria-valuenow={Math.round(progress)}
                  aria-label="Progress toward your turn"
                >
                  <div className="h-full rounded-full bg-signal transition-all duration-700" style={{ width: `${progress}%` }} />
                </div>
                <p className="mt-1 text-sm text-ink-500">Position {token.position} in line</p>
              </div>
            )}
          </div>
        }
      />

      <p className={`mt-4 text-center text-xl font-semibold ${token.nearTurn && waiting ? 'text-[#7a5600]' : ''}`} role="status">
        {token.message}
      </p>
      {waiting && (
        <p className="text-center text-sm text-ink-500">
          You may have to wait approximately {formatWait(token.estimatedWaitMinutes).replace('~', '')}. This is an estimate, not a promise.
        </p>
      )}

      <div className="mt-5">
        <ServingBoard
          compact
          nowServing={token.currentlyServing ? [{ tokenNumber: token.currentlyServing, counterName: null, status: 'SERVING' }] : []}
        />
      </div>

      <Card className="mt-4">
        <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-[16px]">
          <dt className="text-ink-500">Service</dt>
          <dd className="font-semibold">{token.serviceName}</dd>
          <dt className="text-ink-500">Queue</dt>
          <dd className="flex items-center gap-2 font-semibold">
            {token.queueName} <QueueBadge status={token.queueStatus} />
          </dd>
          <dt className="text-ink-500">Branch</dt>
          <dd className="font-semibold">
            {token.branchName}, {token.organizationName}
          </dd>
          <dt className="text-ink-500">Joined</dt>
          <dd className="font-semibold">{formatDateTime(token.joinedAt)}</dd>
          <dt className="text-ink-500">Last updated</dt>
          <dd className="font-semibold">{formatTime(token.updatedAt)}</dd>
        </dl>
        {token.queueStatus === 'PAUSED' && waiting && (
          <p className="mt-3 rounded-md bg-signal-100 px-3 py-2 text-[15px]">This queue is paused. Your place is kept.</p>
        )}
      </Card>

      <div className="mt-4 grid gap-3">
        {notify === 'default' && !finished && (
          <Button variant="secondary" onClick={() => void enableNotifications()}>
            Alert me when it is my turn
          </Button>
        )}
        {notify === 'denied' && !finished && (
          <p className="text-center text-sm text-ink-500">Notifications are blocked in your browser settings. Keep this page open to see updates.</p>
        )}
        {(waiting || token.status === 'CALLED') && (
          <Button variant="ghost" className="text-stop" onClick={() => setConfirmOpen(true)}>
            Leave the queue
          </Button>
        )}
        {finished && (
          <Link to="/join">
            <Button variant="primary" className="w-full">
              Join another queue
            </Button>
          </Link>
        )}
      </div>

      <ConfirmDialog
        open={confirmOpen}
        danger
        title="Leave the queue?"
        message={`Token ${token.tokenNumber} will be cancelled and you will lose your place. You can join again, but at the back of the line.`}
        confirmLabel="Leave queue"
        loading={cancelling}
        onConfirm={() => void cancel()}
        onCancel={() => setConfirmOpen(false)}
      />
    </div>
  );
}

function progressPercent(token: TokenView, startingAhead: number): number {
  if (startingAhead <= 0) return 92;
  return Math.max(6, Math.min(95, ((startingAhead - token.peopleAhead) / startingAhead) * 100));
}

function CallBanner({ token }: { token: TokenView }) {
  return (
    <div className="mb-4 rounded-lg bg-ink px-5 py-4 text-center text-white" role="alert">
      <p className="font-display text-4xl font-extrabold leading-tight text-signal">
        {token.status === 'SERVING' ? 'You are being served' : token.counterName ? `Please proceed to ${token.counterName}` : 'Please proceed to the counter'}
      </p>
      <p className="mt-1 text-ink-300">Show token {token.tokenNumber} to staff.</p>
    </div>
  );
}
