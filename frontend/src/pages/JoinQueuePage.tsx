import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, Card, ConnectionDot, ErrorNotice, QueueBadge, Skeleton, TextField } from '../components/ui';
import { ServingBoard } from '../components/ServingBoard';
import { errorMessage, useQuery } from '../hooks/useApi';
import { useQueueEvents } from '../hooks/useLiveQueue';
import { useToast } from '../hooks/useToast';
import { HttpError } from '../services/http';
import { publicApi, tokenApi } from '../services/api';
import { formatWait, reasonText, rememberToken } from '../utils/format';

/** Landing page for a queue QR code (/join/:ref) and the confirm step of manual joining. */
export default function JoinQueuePage() {
  const { ref = '' } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const { data: queue, error, loading, reload, setData } = useQuery(() => publicApi.queue(ref), [ref]);
  const [name, setName] = useState('');
  const [joining, setJoining] = useState(false);

  const connection = useQueueEvents(
    ref,
    (e) => setData(e.queue),
    () => void reload(),
  );

  async function join() {
    if (!queue) return;
    setJoining(true);
    try {
      const res = await tokenApi.join(queue.queueId, name.trim() || undefined);
      rememberToken(res.token.reference, `${res.token.tokenNumber} · ${res.token.queueName}`);
      navigate(`/queue/token/${res.token.reference}`, { replace: true });
    } catch (e) {
      if (e instanceof HttpError && e.code === 'DUPLICATE_ACTIVE_TOKEN') {
        toast.info('You already have a token in this queue. Open it from "My token".');
        navigate('/token');
      } else {
        toast.error(errorMessage(e));
      }
    } finally {
      setJoining(false);
    }
  }

  if (loading) {
    return (
      <div className="mx-auto max-w-lg px-4 py-10">
        <Skeleton className="h-10 w-2/3" />
        <Skeleton className="mt-6 h-28" />
        <Skeleton className="mt-4 h-40" />
      </div>
    );
  }
  if (error || !queue) {
    return (
      <div className="mx-auto max-w-lg px-4 py-10">
        <h1 className="mb-4 text-4xl">{error?.code === 'QUEUE_NOT_FOUND' ? 'We could not find this queue' : 'Could not load this queue'}</h1>
        <ErrorNotice
          message={error?.code === 'QUEUE_NOT_FOUND' ? 'The link or QR code may be out of date. Ask the front desk for a new one.' : errorMessage(error)}
          onRetry={error?.code === 'QUEUE_NOT_FOUND' ? undefined : () => void reload()}
        />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-lg px-4 py-10">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-ink-500">
            {queue.organizationName} · {queue.branchName}
          </p>
          <h1 className="text-5xl">{queue.name}</h1>
          <p className="mt-1 text-ink-500">{queue.serviceName}</p>
        </div>
        <QueueBadge status={queue.status} />
      </div>

      <div className="mt-6">
        <ServingBoard nowServing={queue.nowServing} lastCalled={queue.lastCalledToken} />
      </div>

      <div className="mt-4 grid grid-cols-2 gap-3">
        <Card>
          <p className="text-sm text-ink-500">People waiting</p>
          <p className="font-display text-4xl font-bold">{queue.waitingCount}</p>
        </Card>
        <Card>
          <p className="text-sm text-ink-500">If you join now</p>
          <p className="font-display text-4xl font-bold">{queue.joinable ? formatWait(queue.estimatedWaitForNewTokenMinutes) : '-'}</p>
        </Card>
      </div>

      <Card className="mt-4">
        {queue.joinable ? (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void join();
            }}
            className="grid gap-4"
          >
            <TextField
              label="What should we call you?"
              hint="Optional. Staff see it when they call you. Leave it empty to stay anonymous."
              maxLength={100}
              value={name}
              onChange={(e) => setName(e.target.value)}
              autoComplete="given-name"
            />
            <Button size="lg" variant="call" type="submit" loading={joining}>
              Get my token
            </Button>
            <p className="text-sm text-ink-500">No account needed. The wait shown is an estimate.</p>
          </form>
        ) : (
          <div role="status">
            <p className="font-display text-2xl font-semibold">Not accepting new tokens right now</p>
            <p className="text-ink-500">{(queue.unavailableReason && reasonText[queue.unavailableReason]) ?? 'Please check back soon.'}</p>
          </div>
        )}
      </Card>
      <div className="mt-3 text-right">
        <ConnectionDot state={connection} />
      </div>
    </div>
  );
}
