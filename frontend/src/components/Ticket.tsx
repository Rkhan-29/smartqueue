import type { ReactNode } from 'react';

interface TicketProps {
  tokenNumber: string;
  caption: string;
  /** Content below the perforation (the stub). */
  stub?: ReactNode;
  tone?: 'waiting' | 'called' | 'done';
  priority?: boolean;
}

const toneClass = {
  waiting: 'bg-surface text-ink',
  called: 'bg-signal text-ink',
  done: 'bg-paper-200 text-ink-500',
} as const;

/** The signature element: a paper queue ticket with a notched tear line. */
export function Ticket({ tokenNumber, caption, stub, tone = 'waiting', priority }: TicketProps) {
  return (
    <div className="drop-shadow-[0_10px_18px_rgba(11,37,69,0.18)]">
      <div className={`ticket relative overflow-hidden rounded-lg ${toneClass[tone]} ${tone === 'called' ? 'animate-callpulse' : ''}`}>
        <div className="px-6 pb-8 pt-6 text-center" style={{ minHeight: '68%' }}>
          <p className="text-[15px] font-semibold opacity-80">{caption}</p>
          <p
            className="font-display text-[88px] font-extrabold leading-[0.95] tracking-tight sm:text-[112px]"
            aria-label={`Token ${tokenNumber}`}
          >
            {tokenNumber}
          </p>
          {priority && <p className="mt-1 text-[15px] font-semibold">Priority token</p>}
        </div>
        <div className="tear mx-4 text-ink" aria-hidden />
        <div className="px-6 pb-5 pt-4">{stub}</div>
      </div>
    </div>
  );
}
