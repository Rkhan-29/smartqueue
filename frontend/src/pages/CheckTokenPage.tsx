import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Button, Card, EmptyState, Skeleton, StatusBadge, TextField } from '../components/ui';
import { useAuth } from '../hooks/useAuth';
import { useQuery } from '../hooks/useApi';
import { tokenApi } from '../services/api';
import { extractReference, formatDateTime, recentTokens } from '../utils/format';

/** "Check my token": tokens remembered on this device, the signed-in account's history, or a pasted link. */
export default function CheckTokenPage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [input, setInput] = useState('');
  const [local] = useState(recentTokens);
  const mine = useQuery(() => (user?.role === 'CUSTOMER' ? tokenApi.mine() : Promise.resolve([])), [user?.id]);

  return (
    <div className="mx-auto max-w-2xl px-4 py-10">
      <h1 className="text-5xl">My token</h1>
      <p className="mt-2 text-ink-500">Find a token you already hold. Tokens issued on this device are listed here.</p>

      <Card className="mt-6">
        <form
          className="flex flex-col gap-3 sm:flex-row sm:items-end"
          onSubmit={(e) => {
            e.preventDefault();
            const ref = extractReference(input);
            if (ref) navigate(`/queue/token/${ref}`);
          }}
        >
          <div className="flex-1">
            <TextField
              label="Token link or reference"
              placeholder="Paste the link you were given"
              value={input}
              onChange={(e) => setInput(e.target.value)}
            />
          </div>
          <Button type="submit" disabled={!input.trim()}>
            Open token
          </Button>
        </form>
      </Card>

      <h2 className="mb-3 mt-8 text-3xl">On this device</h2>
      {local.length === 0 ? (
        <EmptyState
          title="No tokens on this device"
          hint="When you join a queue, your token is saved here so you can find it again."
          action={
            <Link to="/join">
              <Button variant="call">Join a queue</Button>
            </Link>
          }
        />
      ) : (
        <ul className="grid gap-2">
          {local.map((t) => (
            <li key={t.reference}>
              <Link to={`/queue/token/${t.reference}`} className="flex items-center justify-between rounded-lg border border-paper-300/70 bg-surface px-4 py-3 hover:bg-paper-100">
                <span className="font-semibold">{t.label}</span>
                <span className="text-sm text-ink-500">{formatDateTime(new Date(t.at).toISOString())}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}

      {user?.role === 'CUSTOMER' && (
        <>
          <h2 className="mb-3 mt-8 text-3xl">Your account history</h2>
          {mine.data && mine.data.length > 0 ? (
            <ul className="grid gap-2">
              {mine.data.map((t) => (
                <li key={t.reference}>
                  <Link to={`/queue/token/${t.reference}`} className="flex items-center justify-between gap-3 rounded-lg border border-paper-300/70 bg-surface px-4 py-3 hover:bg-paper-100">
                    <span>
                      <span className="font-display text-2xl font-bold">{t.tokenNumber}</span>{' '}
                      <span className="text-ink-500">
                        {t.queueName}, {t.branchName}
                      </span>
                    </span>
                    <StatusBadge status={t.status} />
                  </Link>
                </li>
              ))}
            </ul>
          ) : (
            mine.loading ? <Skeleton className="h-14" /> : <p className="text-ink-500">No tokens on your account yet.</p>
          )}
        </>
      )}
      {!user && (
        <p className="mt-8 text-ink-500">
          Want your history on every device?{' '}
          <Link to="/register" className="font-semibold text-ink underline underline-offset-4">
            Create a free account
          </Link>
          .
        </p>
      )}
    </div>
  );
}
