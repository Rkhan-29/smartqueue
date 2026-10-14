import { Link } from 'react-router-dom';
import { Card, EmptyState, ErrorNotice, PageHeader, QueueBadge, Skeleton } from '../../components/ui';
import { errorMessage, useQuery } from '../../hooks/useApi';
import { staffApi } from '../../services/api';

export default function StaffHome() {
  const { data, loading, error, reload } = useQuery(() => staffApi.queues(), []);

  return (
    <>
      <PageHeader title="Your queues" subtitle="Choose a queue to start calling tokens." />
      {loading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Skeleton className="h-32" />
          <Skeleton className="h-32" />
          <Skeleton className="h-32" />
        </div>
      ) : error ? (
        <ErrorNotice message={errorMessage(error)} onRetry={() => void reload()} />
      ) : data && data.length > 0 ? (
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {data.map((q) => (
            <li key={q.queueId}>
              <Link to={`/staff/queues/${q.queueId}`} className="block rounded-lg focus-visible:outline-info">
                <Card className="h-full hover:border-ink-400">
                  <div className="flex items-start justify-between gap-2">
                    <h2 className="text-3xl">{q.name}</h2>
                    <QueueBadge status={q.status} />
                  </div>
                  <p className="text-ink-500">
                    {q.serviceName} · {q.branchName}
                  </p>
                  <p className="mt-4">
                    <span className="font-display text-5xl font-bold">{q.waitingCount}</span>{' '}
                    <span className="text-ink-500">waiting</span>
                  </p>
                </Card>
              </Link>
            </li>
          ))}
        </ul>
      ) : (
        <EmptyState title="No queues assigned" hint="Ask an administrator to assign you to a queue." />
      )}
    </>
  );
}
