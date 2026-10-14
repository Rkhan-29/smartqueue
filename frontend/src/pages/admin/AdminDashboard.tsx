import { HourlyChart } from '../../components/charts';
import { ErrorNotice, PageHeader, Skeleton, Stat, EmptyState, TableWrap } from '../../components/ui';
import { errorMessage, useQuery } from '../../hooks/useApi';
import { adminApi } from '../../services/api';

export default function AdminDashboard() {
  const { data, loading, error, reload } = useQuery(() => adminApi.dashboard(), []);

  if (loading) {
    return (
      <>
        <PageHeader title="Dashboard" />
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          {Array.from({ length: 8 }, (_, i) => (
            <Skeleton key={i} className="h-24" />
          ))}
        </div>
        <Skeleton className="mt-6 h-80" />
      </>
    );
  }
  if (error || !data) return <ErrorNotice message={errorMessage(error)} onRetry={() => void reload()} />;

  return (
    <>
      <PageHeader title="Dashboard" subtitle="Today across all branches, each in its own local time." />
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Stat label="Tokens today" value={data.totalTokensToday} />
        <Stat label="Completed" value={data.completedToday} tone="good" />
        <Stat label="Waiting now" value={data.waitingNow} />
        <Stat label="Cancelled" value={data.cancelledToday} />
        <Stat label="No-shows" value={data.noShowToday} tone={data.noShowToday > 0 ? 'warn' : 'default'} />
        <Stat label="Avg waiting time" value={`${data.avgWaitMinutes}m`} />
        <Stat label="Avg service time" value={`${data.avgServiceMinutes}m`} />
        <Stat label="Queue utilization" value={`${data.queueUtilizationPercent}%`} hint={`${data.activeQueues} active queues`} />
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-2">
        <HourlyChart data={data.peakHours} title="Peak hours today" />
        <div>
          <h2 className="mb-3 text-2xl">Queues right now</h2>
          {data.queues.length === 0 ? (
            <EmptyState title="No queues yet" hint="Create a queue under Queues to see it here." />
          ) : (
            <TableWrap>
              <table className="w-full">
                <thead>
                  <tr>
                    <th className="table-th">Queue</th>
                    <th className="table-th">Waiting</th>
                    <th className="table-th">Active</th>
                    <th className="table-th">Done</th>
                    <th className="table-th">Avg wait</th>
                    <th className="table-th">In use</th>
                  </tr>
                </thead>
                <tbody>
                  {data.queues.map((q) => (
                    <tr key={q.queueId} className="border-t border-paper-200">
                      <td className="table-td font-semibold">{q.name}</td>
                      <td className="table-td">{q.waiting}</td>
                      <td className="table-td">{q.inProgress}</td>
                      <td className="table-td">{q.completedToday}</td>
                      <td className="table-td">{q.avgWaitMinutes}m</td>
                      <td className="table-td">{q.utilizationPercent}%</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </TableWrap>
          )}
        </div>
      </div>
    </>
  );
}
