import { useState } from 'react';
import { DailyChart, HourlyChart, QueueVolumeChart, WaitChart } from '../../components/charts';
import { EmptyState, ErrorNotice, PageHeader, SelectField, Skeleton, TextField } from '../../components/ui';
import { errorMessage, useQuery } from '../../hooks/useApi';
import { adminApi } from '../../services/api';

const iso = (d: Date) => d.toISOString().slice(0, 10);

export default function AnalyticsPage() {
  const today = new Date();
  const [from, setFrom] = useState(iso(new Date(today.getTime() - 13 * 86_400_000)));
  const [to, setTo] = useState(iso(today));
  const [queueId, setQueueId] = useState('');

  const queues = useQuery(() => adminApi.queues(), []);
  const report = useQuery(() => adminApi.analytics({ from, to, queueId: queueId || undefined }), [from, to, queueId]);

  return (
    <>
      <PageHeader title="Analytics" subtitle="How queues perform over time." />
      <div className="mb-6 grid gap-3 sm:grid-cols-3">
        <TextField label="From" type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
        <TextField label="To" type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} />
        <SelectField label="Queue" value={queueId} onChange={(e) => setQueueId(e.target.value)}>
          <option value="">All queues</option>
          {queues.data?.map((q) => (
            <option key={q.id} value={q.id}>
              {q.name}
            </option>
          ))}
        </SelectField>
      </div>

      {report.loading ? (
        <div className="grid gap-6 lg:grid-cols-2">
          <Skeleton className="h-80" />
          <Skeleton className="h-80" />
        </div>
      ) : report.error || !report.data ? (
        <ErrorNotice message={errorMessage(report.error)} onRetry={() => void report.reload()} />
      ) : report.data.daily.length === 0 ? (
        <EmptyState title="No tokens in this period" hint="Try a wider date range or a different queue." />
      ) : (
        <div className="grid gap-6 lg:grid-cols-2">
          <DailyChart data={report.data.daily} />
          <WaitChart data={report.data.daily} />
          <HourlyChart data={report.data.hourly} title="Peak hours" subtitle="Tokens issued per hour across the period" />
          <QueueVolumeChart data={report.data.byQueue} />
        </div>
      )}
    </>
  );
}
