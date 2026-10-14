import { useState } from 'react';
import { EmptyState, ErrorNotice, PageHeader, Pagination, SelectField, Skeleton, StatusBadge, TableWrap, TextField, Card } from '../../components/ui';
import { errorMessage, useQuery } from '../../hooks/useApi';
import { adminApi } from '../../services/api';
import { formatDateTime, formatTime, statusLabel } from '../../utils/format';
import type { TokenStatus } from '../../types/api';

export function CustomersPage() {
  const [page, setPage] = useState(0);
  const { data, loading, error, reload } = useQuery(() => adminApi.customers(page), [page]);
  return (
    <>
      <PageHeader title="Customers" subtitle="Registered customer accounts. Guests who join without an account are not listed here." />
      {loading ? (
        <Skeleton className="h-48" />
      ) : error ? (
        <ErrorNotice message={errorMessage(error)} onRetry={() => void reload()} />
      ) : !data || data.items.length === 0 ? (
        <EmptyState title="No customer accounts yet" hint="Customers can join queues as guests, so accounts are optional." />
      ) : (
        <>
          <TableWrap>
            <table className="w-full">
              <thead>
                <tr>
                  <th className="table-th">Name</th>
                  <th className="table-th">Email</th>
                  <th className="table-th">Joined</th>
                  <th className="table-th">Last sign-in</th>
                  <th className="table-th">Status</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((u) => (
                  <tr key={u.id} className="border-t border-paper-200">
                    <td className="table-td font-semibold">{u.fullName}</td>
                    <td className="table-td">{u.email}</td>
                    <td className="table-td">{formatDateTime(u.createdAt)}</td>
                    <td className="table-td">{formatDateTime(u.lastLoginAt)}</td>
                    <td className="table-td">{u.status === 'ACTIVE' ? 'Active' : 'Disabled'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </TableWrap>
          <Pagination page={data.page} totalPages={data.totalPages} onPage={setPage} />
        </>
      )}
    </>
  );
}

export function TokensPage() {
  const [page, setPage] = useState(0);
  const [queueId, setQueueId] = useState('');
  const [status, setStatus] = useState('');
  const [date, setDate] = useState('');
  const queues = useQuery(() => adminApi.queues(), []);
  const { data, loading, error, reload } = useQuery(
    () => adminApi.tokens({ queueId: queueId || undefined, status: status || undefined, date: date || undefined, page }),
    [page, queueId, status, date],
  );
  const reset = <T,>(set: (v: T) => void) => (v: T) => {
    set(v);
    setPage(0);
  };

  return (
    <>
      <PageHeader title="Tokens" subtitle="Every token issued, newest first." />
      <Card className="mb-5 grid gap-3 sm:grid-cols-3">
        <SelectField label="Queue" value={queueId} onChange={(e) => reset(setQueueId)(e.target.value)}>
          <option value="">All queues</option>
          {queues.data?.map((q) => (
            <option key={q.id} value={q.id}>
              {q.name}
            </option>
          ))}
        </SelectField>
        <SelectField label="Status" value={status} onChange={(e) => reset(setStatus)(e.target.value)}>
          <option value="">Any status</option>
          {(Object.keys(statusLabel) as TokenStatus[]).map((s) => (
            <option key={s} value={s}>
              {statusLabel[s]}
            </option>
          ))}
        </SelectField>
        <TextField label="Date" type="date" value={date} onChange={(e) => reset(setDate)(e.target.value)} />
      </Card>
      {loading ? (
        <Skeleton className="h-64" />
      ) : error ? (
        <ErrorNotice message={errorMessage(error)} onRetry={() => void reload()} />
      ) : !data || data.items.length === 0 ? (
        <EmptyState title="No tokens match" hint="Change the filters to see more." />
      ) : (
        <>
          <TableWrap>
            <table className="w-full">
              <thead>
                <tr>
                  <th className="table-th">Token</th>
                  <th className="table-th">Queue</th>
                  <th className="table-th">Customer</th>
                  <th className="table-th">Joined</th>
                  <th className="table-th">Called</th>
                  <th className="table-th">Finished</th>
                  <th className="table-th">Status</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((t) => (
                  <tr key={t.id} className="border-t border-paper-200">
                    <td className="table-td font-display text-xl font-bold">
                      {t.tokenNumber}
                      {t.priority && <span className="ml-2 text-sm font-sans font-semibold text-ink-500">Priority</span>}
                    </td>
                    <td className="table-td">{t.queueName}</td>
                    <td className="table-td">{t.customerName ?? (t.guest ? 'Guest' : 'Account holder')}</td>
                    <td className="table-td">{formatDateTime(t.createdAt)}</td>
                    <td className="table-td">{formatTime(t.calledAt)}</td>
                    <td className="table-td">{formatTime(t.completedAt)}</td>
                    <td className="table-td">
                      <StatusBadge status={t.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </TableWrap>
          <Pagination page={data.page} totalPages={data.totalPages} onPage={setPage} />
          <p className="mt-2 text-sm text-ink-500">{data.totalItems} tokens</p>
        </>
      )}
    </>
  );
}

export function AuditPage() {
  const [page, setPage] = useState(0);
  const [action, setAction] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const { data, loading, error, reload } = useQuery(
    () => adminApi.auditLogs({ action: action.trim() || undefined, from: from || undefined, to: to || undefined, page }),
    [page, action, from, to],
  );
  return (
    <>
      <PageHeader title="Audit logs" subtitle="Who did what, and when. Entries cannot be edited." />
      <Card className="mb-5 grid gap-3 sm:grid-cols-3">
        <TextField label="Action" placeholder="TOKEN_CALLED" value={action} onChange={(e) => { setAction(e.target.value.toUpperCase()); setPage(0); }} hint="Exact action name" />
        <TextField label="From" type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0); }} />
        <TextField label="To" type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0); }} />
      </Card>
      {loading ? (
        <Skeleton className="h-64" />
      ) : error ? (
        <ErrorNotice message={errorMessage(error)} onRetry={() => void reload()} />
      ) : !data || data.items.length === 0 ? (
        <EmptyState title="No activity found" hint="Try a different action or date range." />
      ) : (
        <>
          <TableWrap>
            <table className="w-full">
              <thead>
                <tr>
                  <th className="table-th">When</th>
                  <th className="table-th">Who</th>
                  <th className="table-th">Action</th>
                  <th className="table-th">Item</th>
                  <th className="table-th">Details</th>
                  <th className="table-th">IP</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((a) => (
                  <tr key={a.id} className="border-t border-paper-200">
                    <td className="table-td whitespace-nowrap">{formatDateTime(a.createdAt)}</td>
                    <td className="table-td">{a.actor}</td>
                    <td className="table-td font-semibold">{a.action}</td>
                    <td className="table-td">{a.entityType}</td>
                    <td className="table-td max-w-xs truncate text-sm text-ink-500" title={a.metadata ?? ''}>
                      {a.metadata ?? '-'}
                    </td>
                    <td className="table-td text-sm text-ink-500">{a.ipAddress ?? '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </TableWrap>
          <Pagination page={data.page} totalPages={data.totalPages} onPage={setPage} />
        </>
      )}
    </>
  );
}

export function SettingsPage() {
  const { data, loading, error, reload } = useQuery(() => adminApi.settings(), []);
  return (
    <>
      <PageHeader title="Settings" subtitle="System-wide values set through environment variables. Change them in your deployment, then restart." />
      {loading ? (
        <Skeleton className="h-48" />
      ) : error || !data ? (
        <ErrorNotice message={errorMessage(error)} onRetry={() => void reload()} />
      ) : (
        <Card>
          <dl className="grid gap-x-8 gap-y-3 sm:grid-cols-[max-content_1fr]">
            <dt className="text-ink-500">Public site URL (used in QR codes)</dt>
            <dd className="break-all font-semibold">{data.publicBaseUrl}</dd>
            <dt className="text-ink-500">Sign-in session length</dt>
            <dd className="font-semibold">{data.accessTokenTtlMinutes} minutes, renewed for up to {data.refreshTokenTtlDays} days</dd>
            <dt className="text-ink-500">Account lock</dt>
            <dd className="font-semibold">
              After {data.loginMaxAttempts} wrong passwords, for {data.loginLockoutMinutes} minutes
            </dd>
            <dt className="text-ink-500">Sign-in rate limit</dt>
            <dd className="font-semibold">{data.authRateLimitPerMinute} requests per minute per address</dd>
            <dt className="text-ink-500">Token creation rate limit</dt>
            <dd className="font-semibold">{data.joinRateLimitPerMinute} requests per minute per address</dd>
            <dt className="text-ink-500">Demo data</dt>
            <dd className="font-semibold">{data.demoDataEnabled ? 'Enabled. Turn this off in production.' : 'Off'}</dd>
          </dl>
          <p className="mt-5 text-sm text-ink-500">Queue-level rules such as prefixes, hours and priority are under Queues.</p>
        </Card>
      )}
    </>
  );
}
