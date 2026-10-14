import { useState, type ReactNode } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { DayPoint, HourBucket, QueueRow } from '../types/api';

// Validated categorical order (scripts/validate_palette.js): blue, green, amber, red
export const chartColors = { blue: '#2F6FDE', green: '#14866D', amber: '#E5A100', red: '#C8344B' };
const axis = { stroke: '#9AA9BF', fontSize: 13, tickLine: false, axisLine: false } as const;

/** Every chart ships with a table view so values never depend on color or hover alone. */
export function ChartCard({
  title,
  subtitle,
  table,
  children,
}: {
  title: string;
  subtitle?: string;
  table: { head: string[]; rows: (string | number)[][] };
  children: ReactNode;
}) {
  const [asTable, setAsTable] = useState(false);
  return (
    <section className="rounded-lg border border-paper-300/70 bg-surface p-5">
      <div className="mb-3 flex items-start justify-between gap-3">
        <div>
          <h2 className="text-2xl">{title}</h2>
          {subtitle && <p className="text-sm text-ink-500">{subtitle}</p>}
        </div>
        <button
          type="button"
          onClick={() => setAsTable((v) => !v)}
          className="rounded-md px-2 py-1 text-sm font-semibold text-ink-500 hover:bg-paper-200"
          aria-pressed={asTable}
        >
          {asTable ? 'Show chart' : 'Show table'}
        </button>
      </div>
      {asTable ? (
        <div className="max-h-72 overflow-auto">
          <table className="w-full text-[15px]">
            <thead>
              <tr>
                {table.head.map((h) => (
                  <th key={h} className="table-th">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {table.rows.map((r, i) => (
                <tr key={i} className="border-t border-paper-200">
                  {r.map((c, j) => (
                    <td key={j} className="table-td">
                      {c}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="h-72">{children}</div>
      )}
    </section>
  );
}

const tooltipStyle = {
  contentStyle: { borderRadius: 8, border: '1px solid #CFD8E3', boxShadow: '0 4px 12px rgba(11,37,69,.12)', fontSize: 14 },
  labelStyle: { color: '#0B2545', fontWeight: 600 },
  itemStyle: { color: '#0B2545' },
  cursor: { fill: 'rgba(11,37,69,0.05)' },
};

const hourLabel = (h: number) => `${h % 12 === 0 ? 12 : h % 12}${h < 12 ? 'am' : 'pm'}`;

export function HourlyChart({ data, title = 'Busiest hours', subtitle }: { data: HourBucket[]; title?: string; subtitle?: string }) {
  const byHour = new Map(data.map((d) => [d.hour, d.tokens]));
  const rows = Array.from({ length: 24 }, (_, h) => ({ hour: h, label: hourLabel(h), tokens: byHour.get(h) ?? 0 }));
  const visible = rows.some((r) => r.tokens > 0) ? rows.filter((r) => r.hour >= 6 && r.hour <= 22) : rows.filter((r) => r.hour >= 6 && r.hour <= 22);
  return (
    <ChartCard
      title={title}
      subtitle={subtitle ?? 'Tokens issued per hour, in each branch\'s local time'}
      table={{ head: ['Hour', 'Tokens'], rows: visible.map((r) => [r.label, r.tokens]) }}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={visible} margin={{ top: 8, right: 8, left: -16, bottom: 0 }} barCategoryGap={4}>
          <CartesianGrid stroke="#E2E8EF" vertical={false} />
          <XAxis dataKey="label" {...axis} interval={1} />
          <YAxis {...axis} allowDecimals={false} />
          <Tooltip {...tooltipStyle} formatter={(v: number) => [v, 'Tokens']} />
          <Bar dataKey="tokens" name="Tokens" fill={chartColors.blue} radius={[4, 4, 0, 0]} maxBarSize={28} />
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  );
}

export function DailyChart({ data }: { data: DayPoint[] }) {
  const rows = data.map((d) => ({ ...d, label: d.date.slice(5) }));
  return (
    <ChartCard
      title="Tokens per day"
      subtitle="Completed, cancelled and no-show tokens"
      table={{
        head: ['Date', 'Total', 'Completed', 'Cancelled', 'No-show', 'Avg wait (min)'],
        rows: data.map((d) => [d.date, d.total, d.completed, d.cancelled, d.noShow, d.avgWaitMinutes]),
      }}
    >
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={rows} margin={{ top: 8, right: 16, left: -16, bottom: 0 }}>
          <CartesianGrid stroke="#E2E8EF" vertical={false} />
          <XAxis dataKey="label" {...axis} />
          <YAxis {...axis} allowDecimals={false} />
          <Tooltip {...tooltipStyle} cursor={{ stroke: '#9AA9BF' }} />
          <Legend verticalAlign="top" height={30} iconType="plainline" wrapperStyle={{ fontSize: 14, color: '#0B2545' }} />
          <Line type="monotone" dataKey="completed" name="Completed" stroke={chartColors.green} strokeWidth={2} dot={{ r: 4, strokeWidth: 2, stroke: '#fff' }} />
          <Line type="monotone" dataKey="cancelled" name="Cancelled" stroke={chartColors.amber} strokeWidth={2} dot={{ r: 4, strokeWidth: 2, stroke: '#fff' }} />
          <Line type="monotone" dataKey="noShow" name="No-show" stroke={chartColors.red} strokeWidth={2} dot={{ r: 4, strokeWidth: 2, stroke: '#fff' }} />
        </LineChart>
      </ResponsiveContainer>
    </ChartCard>
  );
}

export function WaitChart({ data }: { data: DayPoint[] }) {
  const rows = data.map((d) => ({ label: d.date.slice(5), wait: d.avgWaitMinutes }));
  return (
    <ChartCard
      title="Average wait"
      subtitle="Minutes from joining to being called"
      table={{ head: ['Date', 'Avg wait (min)'], rows: data.map((d) => [d.date, d.avgWaitMinutes]) }}
    >
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={rows} margin={{ top: 8, right: 16, left: -16, bottom: 0 }}>
          <CartesianGrid stroke="#E2E8EF" vertical={false} />
          <XAxis dataKey="label" {...axis} />
          <YAxis {...axis} unit="m" />
          <Tooltip {...tooltipStyle} cursor={{ stroke: '#9AA9BF' }} formatter={(v: number) => [`${v} min`, 'Avg wait']} />
          <Line type="monotone" dataKey="wait" name="Avg wait" stroke={chartColors.blue} strokeWidth={2} dot={{ r: 4, strokeWidth: 2, stroke: '#fff' }} />
        </LineChart>
      </ResponsiveContainer>
    </ChartCard>
  );
}

export function QueueVolumeChart({ data }: { data: QueueRow[] }) {
  return (
    <ChartCard
      title="Volume by queue"
      subtitle="Completed and cancelled tokens in the period"
      table={{
        head: ['Queue', 'Total', 'Completed', 'Cancelled', 'Avg wait (min)', 'Avg service (min)'],
        rows: data.map((q) => [q.name, q.total, q.completed, q.cancelled, q.avgWaitMinutes, q.avgServiceMinutes]),
      }}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} layout="vertical" margin={{ top: 8, right: 16, left: 24, bottom: 0 }} barCategoryGap={8}>
          <CartesianGrid stroke="#E2E8EF" horizontal={false} />
          <XAxis type="number" {...axis} allowDecimals={false} />
          <YAxis type="category" dataKey="name" {...axis} width={110} />
          <Tooltip {...tooltipStyle} />
          <Legend verticalAlign="top" height={30} wrapperStyle={{ fontSize: 14, color: '#0B2545' }} />
          <Bar dataKey="completed" name="Completed" fill={chartColors.green} radius={[0, 4, 4, 0]} maxBarSize={18} />
          <Bar dataKey="cancelled" name="Cancelled" fill={chartColors.amber} radius={[0, 4, 4, 0]} maxBarSize={18} />
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  );
}
