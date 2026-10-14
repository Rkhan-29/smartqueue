import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ConfirmDialog, Modal } from '../../components/ConfirmDialog';
import { ServingBoard } from '../../components/ServingBoard';
import {
  Button,
  Card,
  ConnectionDot,
  EmptyState,
  ErrorNotice,
  QueueBadge,
  SelectField,
  Skeleton,
  Stat,
  StatusBadge,
  TextField,
} from '../../components/ui';
import { errorMessage, useQuery } from '../../hooks/useApi';
import { useQueueEvents } from '../../hooks/useLiveQueue';
import { useToast } from '../../hooks/useToast';
import { staffApi } from '../../services/api';
import type { StaffToken } from '../../types/api';
import { formatSeconds, formatTime } from '../../utils/format';

type TokenAction = 'recall' | 'serving' | 'complete' | 'skip' | 'no-show' | 'cancel' | 'requeue';
const pastTense: Record<TokenAction, string> = {
  recall: 'recalled',
  serving: 'now being served',
  complete: 'completed',
  skip: 'skipped',
  'no-show': 'marked as no-show',
  cancel: 'cancelled',
  requeue: 'back in line',
};

export default function StaffQueuePage() {
  const { queueId = '' } = useParams();
  const toast = useToast();
  const { data: state, error, loading, reload } = useQuery(() => staffApi.state(queueId), [queueId]);
  const [busy, setBusy] = useState<string | null>(null);
  const [counterId, setCounterId] = useState<string>(() => {
    try {
      return localStorage.getItem(`sq.counter.${queueId}`) ?? '';
    } catch {
      return '';
    }
  });
  const [confirm, setConfirm] = useState<{ token: StaffToken; action: TokenAction } | null>(null);
  const [walkInOpen, setWalkInOpen] = useState(false);

  // Coalesce bursts of events into one refetch
  const timer = useRef<number | undefined>(undefined);
  const scheduleReload = useCallback(() => {
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => void reload(), 150);
  }, [reload]);
  useEffect(() => () => window.clearTimeout(timer.current), []);

  const connection = useQueueEvents(state?.queue.publicRef, scheduleReload, scheduleReload);

  // Tick so "waiting for" timers move without refetching
  const [, setTick] = useState(0);
  useEffect(() => {
    const id = window.setInterval(() => setTick((n) => n + 1), 1000);
    return () => window.clearInterval(id);
  }, []);

  function chooseCounter(id: string) {
    setCounterId(id);
    try {
      localStorage.setItem(`sq.counter.${queueId}`, id);
    } catch {
      /* ignore */
    }
  }

  async function run(key: string, fn: () => Promise<unknown>, success: string) {
    setBusy(key);
    try {
      await fn();
      toast.success(success);
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(null);
      void reload();
    }
  }

  const callNext = () =>
    run('next', async () => {
      const t = await staffApi.next(queueId, counterId || null);
      toast.info(`Called ${t.tokenNumber}`);
    }, 'Next token called');

  const act = (t: StaffToken, action: TokenAction) =>
    run(`${action}:${t.id}`, () => staffApi.action(t.id, action), `${t.tokenNumber} ${pastTense[action]}`);

  if (loading) {
    return (
      <div className="grid gap-4">
        <Skeleton className="h-16 w-1/2" />
        <Skeleton className="h-40" />
        <Skeleton className="h-64" />
      </div>
    );
  }
  if (error || !state) {
    return (
      <>
        <ErrorNotice message={errorMessage(error)} onRetry={() => void reload()} />
        <Link to="/staff" className="mt-4 inline-block font-semibold underline underline-offset-4">
          Back to your queues
        </Link>
      </>
    );
  }

  const { queue, stats, active, waiting, recent, counters } = state;
  const paused = queue.status === 'PAUSED';
  const activeCounters = counters.filter((c) => c.active);
  const canCallNext = waiting.length > 0 && !paused && queue.status !== 'CLOSED';

  return (
    <>
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div>
          <Link to="/staff" className="text-sm text-ink-500 underline underline-offset-4">
            All queues
          </Link>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-5xl">{queue.name}</h1>
            <QueueBadge status={queue.status} />
          </div>
          <p className="text-ink-500">
            {queue.serviceName} · {queue.branchName}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <ConnectionDot state={connection} />
          <Button variant="secondary" onClick={() => setWalkInOpen(true)}>
            Add walk-in
          </Button>
          <Button
            variant="secondary"
            loading={busy === 'pause'}
            onClick={() =>
              void run('pause', () => (paused ? staffApi.resume(queueId) : staffApi.pause(queueId)), paused ? 'Queue resumed' : 'Queue paused')
            }
          >
            {paused ? 'Resume queue' : 'Pause queue'}
          </Button>
        </div>
      </div>

      {paused && (
        <p className="mb-4 rounded-md bg-signal-100 px-4 py-3" role="status">
          This queue is paused. Customers cannot join and NEXT is disabled until you resume.
        </p>
      )}

      <div className="grid gap-5 lg:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)]">
        <div className="grid content-start gap-5">
          <Card>
            <div className="mb-4 grid gap-3 sm:grid-cols-[1fr_auto] sm:items-end">
              <ServingBoard nowServing={queue.nowServing} lastCalled={queue.lastCalledToken} />
              {activeCounters.length > 0 && (
                <SelectField label="Calling from" value={counterId} onChange={(e) => chooseCounter(e.target.value)}>
                  <option value="">No counter</option>
                  {activeCounters.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                      {c.currentToken ? ` (busy: ${c.currentToken})` : ''}
                    </option>
                  ))}
                </SelectField>
              )}
            </div>

            <Button
              variant="call"
              className="min-h-[96px] w-full text-[34px] font-extrabold tracking-tight"
              disabled={!canCallNext}
              loading={busy === 'next'}
              onClick={() => void callNext()}
            >
              {waiting.length > 0 ? `Next: ${state.nextToken?.tokenNumber}` : 'No one waiting'}
            </Button>

            <h2 className="mb-2 mt-6 text-3xl">At the counter</h2>
            {active.length === 0 ? (
              <p className="text-ink-500">Nobody has been called yet. Press Next to call the first token.</p>
            ) : (
              <ul className="grid gap-3">
                {active.map((t) => (
                  <li key={t.id} className="rounded-lg border-2 border-signal bg-signal-100/50 p-4">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div>
                        <span className="font-display text-5xl font-extrabold">{t.tokenNumber}</span>
                        {t.priority && <span className="ml-2 rounded-full bg-ink px-2 py-0.5 text-sm font-semibold text-white">Priority</span>}
                        <p className="text-ink-500">
                          {[t.customerName, t.counterName].filter(Boolean).join(' · ') || 'Anonymous'}
                        </p>
                      </div>
                      <StatusBadge status={t.status} />
                    </div>
                    <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-4">
                      {t.status === 'CALLED' && (
                        <Button size="lg" variant="primary" loading={busy === `serving:${t.id}`} onClick={() => void act(t, 'serving')}>
                          Start
                        </Button>
                      )}
                      <Button size="lg" variant="primary" loading={busy === `complete:${t.id}`} onClick={() => void act(t, 'complete')}>
                        Complete
                      </Button>
                      {t.status === 'CALLED' && (
                        <>
                          <Button size="lg" variant="secondary" loading={busy === `recall:${t.id}`} onClick={() => void act(t, 'recall')}>
                            Recall
                          </Button>
                          <Button size="lg" variant="secondary" loading={busy === `skip:${t.id}`} onClick={() => void act(t, 'skip')}>
                            Skip
                          </Button>
                          <Button size="lg" variant="secondary" onClick={() => setConfirm({ token: t, action: 'no-show' })}>
                            No-show
                          </Button>
                        </>
                      )}
                      <Button size="lg" variant="ghost" className="text-stop" onClick={() => setConfirm({ token: t, action: 'cancel' })}>
                        Cancel
                      </Button>
                    </div>
                    {t.calledAt && t.status === 'CALLED' && (
                      <p className="mt-2 text-sm text-ink-500">Called {formatSeconds((Date.now() - new Date(t.calledAt).getTime()) / 1000)} ago</p>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </Card>

          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <Stat label="In queue" value={stats.waiting} />
            <Stat label="Avg wait" value={`${stats.avgWaitMinutes}m`} />
            <Stat label="Avg service" value={`${stats.avgServiceMinutes}m`} />
            <Stat label="Completed today" value={stats.completedToday} tone="good" />
            <Stat label="Cancelled today" value={stats.cancelledToday} />
            <Stat label="Skipped today" value={stats.skippedToday} />
            <Stat label="No-shows today" value={stats.noShowToday} tone={stats.noShowToday > 0 ? 'warn' : 'default'} />
            <Stat label="Tokens today" value={stats.totalToday} />
          </div>
        </div>

        <div className="grid content-start gap-5">
          <Card>
            <h2 className="mb-3 text-3xl">Waiting ({waiting.length})</h2>
            {waiting.length === 0 ? (
              <EmptyState title="The line is clear" hint="New tokens appear here as people join." />
            ) : (
              <ol className="grid max-h-[520px] gap-2 overflow-y-auto pr-1">
                {waiting.map((t, i) => (
                  <li key={t.id} className="flex items-center justify-between gap-2 rounded-md border border-paper-300/70 px-3 py-2">
                    <div className="min-w-0">
                      <span className="font-display text-2xl font-bold">{t.tokenNumber}</span>
                      {t.priority && <span className="ml-2 rounded-full bg-ink px-2 py-0.5 text-xs font-semibold text-white">Priority</span>}
                      <p className="truncate text-sm text-ink-500">
                        {t.customerName ?? 'Anonymous'} · waiting {formatSeconds((Date.now() - new Date(t.createdAt).getTime()) / 1000)}
                      </p>
                    </div>
                    <div className="flex shrink-0 gap-1">
                      {i > 0 && (
                        <Button size="sm" variant="ghost" aria-label={`Move ${t.tokenNumber} to the front`} onClick={() => void run(`move:${t.id}`, () => staffApi.move(t.id, 1), `${t.tokenNumber} moved to the front`)}>
                          To front
                        </Button>
                      )}
                      <Button size="sm" variant="ghost" className="text-stop" aria-label={`Cancel ${t.tokenNumber}`} onClick={() => setConfirm({ token: t, action: 'cancel' })}>
                        Cancel
                      </Button>
                    </div>
                  </li>
                ))}
              </ol>
            )}
          </Card>

          <Card>
            <h2 className="mb-3 text-3xl">Recently finished</h2>
            {recent.length === 0 ? (
              <p className="text-ink-500">Nothing finished yet today.</p>
            ) : (
              <ul className="grid gap-2">
                {recent.map((t) => (
                  <li key={t.id} className="flex items-center justify-between gap-2 text-[15px]">
                    <span>
                      <span className="font-display text-xl font-bold">{t.tokenNumber}</span>{' '}
                      <span className="text-ink-500">{formatTime(t.completedAt ?? t.calledAt ?? t.createdAt)}</span>
                    </span>
                    <span className="flex items-center gap-2">
                      <StatusBadge status={t.status} />
                      {t.status === 'SKIPPED' && (
                        <Button size="sm" variant="secondary" loading={busy === `requeue:${t.id}`} onClick={() => void act(t, 'requeue')}>
                          Back in line
                        </Button>
                      )}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      </div>

      <ConfirmDialog
        open={!!confirm}
        danger
        title={confirm?.action === 'no-show' ? 'Mark as no-show?' : 'Cancel this token?'}
        message={
          confirm
            ? confirm.action === 'no-show'
              ? `Token ${confirm.token.tokenNumber} did not arrive. It cannot be put back in line.`
              : `Token ${confirm.token.tokenNumber} will be cancelled and the customer is told. This cannot be undone.`
            : ''
        }
        confirmLabel={confirm?.action === 'no-show' ? 'Mark no-show' : 'Cancel token'}
        loading={!!confirm && busy === `${confirm.action}:${confirm.token.id}`}
        onCancel={() => setConfirm(null)}
        onConfirm={() => {
          if (!confirm) return;
          void act(confirm.token, confirm.action).then(() => setConfirm(null));
        }}
      />

      <WalkInDialog
        open={walkInOpen}
        priorityEnabled={queue.priorityEnabled}
        onClose={() => setWalkInOpen(false)}
        onCreate={async (priority, name) => {
          const res = await staffApi.walkIn(queueId, priority, name);
          toast.success(`Issued ${res.token.tokenNumber}`);
          setWalkInOpen(false);
          void reload();
        }}
      />
    </>
  );
}

function WalkInDialog({
  open,
  priorityEnabled,
  onClose,
  onCreate,
}: {
  open: boolean;
  priorityEnabled: boolean;
  onClose: () => void;
  onCreate: (priority: boolean, name?: string) => Promise<void>;
}) {
  const toast = useToast();
  const [name, setName] = useState('');
  const [priority, setPriority] = useState(false);
  const [busy, setBusy] = useState(false);
  return (
    <Modal open={open} title="Add a walk-in" onClose={onClose}>
      <form
        className="grid gap-4"
        onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          try {
            await onCreate(priority, name.trim() || undefined);
            setName('');
            setPriority(false);
          } catch (err) {
            toast.error(errorMessage(err));
          } finally {
            setBusy(false);
          }
        }}
      >
        <TextField label="Name (optional)" value={name} maxLength={100} onChange={(e) => setName(e.target.value)} />
        {priorityEnabled && (
          <label className="flex items-center gap-3">
            <input type="checkbox" className="h-5 w-5 accent-ink" checked={priority} onChange={(e) => setPriority(e.target.checked)} />
            <span className="font-semibold">Priority token</span>
          </label>
        )}
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="secondary" onClick={onClose}>
            Close
          </Button>
          <Button type="submit" variant="call" loading={busy}>
            Issue token
          </Button>
        </div>
      </form>
    </Modal>
  );
}
