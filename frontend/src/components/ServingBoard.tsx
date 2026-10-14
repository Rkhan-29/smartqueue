import type { ServingEntry } from '../types/api';

/** Amber-on-navy call board, the same pattern as the display screens in waiting rooms. */
export function ServingBoard({
  nowServing,
  lastCalled,
  compact,
}: {
  nowServing: ServingEntry[];
  lastCalled?: string | null;
  compact?: boolean;
}) {
  const entries = nowServing.length > 0 ? nowServing : null;
  return (
    <div className="rounded-lg bg-ink px-5 py-4 text-white" aria-live="polite">
      <p className="text-[15px] text-ink-300">Now serving</p>
      {entries ? (
        <ul className={`mt-1 grid gap-x-8 gap-y-1 ${entries.length > 1 && !compact ? 'sm:grid-cols-2' : ''}`}>
          {entries.map((e) => (
            <li key={e.tokenNumber} className="flex items-baseline gap-3">
              <span className={`led font-extrabold text-signal ${compact ? 'text-5xl' : 'text-6xl'}`}>{e.tokenNumber}</span>
              {e.counterName && <span className="text-lg text-ink-300">{e.counterName}</span>}
            </li>
          ))}
        </ul>
      ) : (
        <p className="mt-1 text-xl text-ink-300">{lastCalled ? `Last called: ${lastCalled}` : 'No one has been called yet'}</p>
      )}
    </div>
  );
}
