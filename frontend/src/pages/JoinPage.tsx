import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Card, EmptyState, ErrorNotice, QueueBadge, SelectField, Skeleton, Button } from '../components/ui';
import { useQuery, errorMessage } from '../hooks/useApi';
import { publicApi } from '../services/api';
import { formatWait, reasonText } from '../utils/format';

/** Method 1: choose organization, branch and service, then open the queue's join page. */
export default function JoinPage() {
  const navigate = useNavigate();
  const [orgId, setOrgId] = useState('');
  const [branchId, setBranchId] = useState('');

  const orgs = useQuery(() => publicApi.organizations(), []);
  const branches = useQuery(() => (orgId ? publicApi.branches(orgId) : Promise.resolve([])), [orgId]);
  const queues = useQuery(() => (branchId ? publicApi.branchQueues(branchId) : Promise.resolve([])), [branchId]);

  useEffect(() => {
    if (orgs.data?.length === 1 && !orgId) setOrgId(orgs.data[0].id);
  }, [orgs.data, orgId]);
  useEffect(() => {
    if (branches.data?.length === 1 && !branchId) setBranchId(branches.data[0].id);
  }, [branches.data, branchId]);

  return (
    <div className="mx-auto max-w-3xl px-4 py-10">
      <h1 className="text-5xl">Join a queue</h1>
      <p className="mt-2 text-ink-500">
        Choose where you are. Or{' '}
        <Link to="/scan" className="font-semibold text-ink underline underline-offset-4">
          scan the QR code
        </Link>{' '}
        if you are at the venue.
      </p>

      <Card className="mt-6 grid gap-4 sm:grid-cols-2">
        {orgs.loading ? (
          <Skeleton className="h-[74px] sm:col-span-2" />
        ) : orgs.error ? (
          <div className="sm:col-span-2">
            <ErrorNotice message={errorMessage(orgs.error)} onRetry={() => void orgs.reload()} />
          </div>
        ) : (
          <>
            <SelectField
              label="Organization"
              value={orgId}
              onChange={(e) => {
                setOrgId(e.target.value);
                setBranchId('');
              }}
            >
              <option value="">Select an organization</option>
              {orgs.data?.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.name}
                </option>
              ))}
            </SelectField>
            <SelectField label="Branch" value={branchId} disabled={!orgId} onChange={(e) => setBranchId(e.target.value)}>
              <option value="">{orgId ? 'Select a branch' : 'Choose an organization first'}</option>
              {branches.data?.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name}
                  {b.city ? `, ${b.city}` : ''}
                </option>
              ))}
            </SelectField>
          </>
        )}
      </Card>

      {branchId && (
        <div className="mt-6">
          <h2 className="mb-3 text-3xl">Services at this branch</h2>
          {queues.loading ? (
            <div className="grid gap-3">
              <Skeleton className="h-24" />
              <Skeleton className="h-24" />
            </div>
          ) : queues.error ? (
            <ErrorNotice message={errorMessage(queues.error)} onRetry={() => void queues.reload()} />
          ) : queues.data && queues.data.length > 0 ? (
            <ul className="grid gap-3">
              {queues.data.map((q) => (
                <li key={q.queueId}>
                  <Card className="flex flex-wrap items-center justify-between gap-4">
                    <div>
                      <div className="flex items-center gap-2">
                        <h3 className="text-2xl">{q.name}</h3>
                        <QueueBadge status={q.status} />
                      </div>
                      <p className="text-ink-500">
                        {q.serviceName} · {q.waitingCount} waiting · {q.joinable ? formatWait(q.estimatedWaitForNewTokenMinutes) : '-'}
                      </p>
                      {!q.joinable && q.unavailableReason && (
                        <p className="mt-1 text-sm font-semibold text-stop">{reasonText[q.unavailableReason] ?? 'Not accepting new tokens.'}</p>
                      )}
                    </div>
                    <Button variant={q.joinable ? 'call' : 'secondary'} onClick={() => navigate(`/join/${q.publicRef}`)}>
                      {q.joinable ? 'Join queue' : 'View queue'}
                    </Button>
                  </Card>
                </li>
              ))}
            </ul>
          ) : (
            <EmptyState title="No open queues here" hint="This branch has no queues set up yet. Ask the front desk." />
          )}
        </div>
      )}
    </div>
  );
}
