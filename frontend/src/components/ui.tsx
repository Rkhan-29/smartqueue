import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react';
import { forwardRef, useId } from 'react';
import type { QueueStatus, TokenStatus } from '../types/api';
import type { ConnectionState } from '../services/realtime';
import { statusLabel } from '../utils/format';

type Variant = 'primary' | 'call' | 'secondary' | 'ghost' | 'danger';
type Size = 'sm' | 'md' | 'lg';

const variants: Record<Variant, string> = {
  primary: 'bg-ink text-white hover:bg-ink-700 disabled:bg-ink-300',
  call: 'bg-signal text-ink hover:bg-signal-600 disabled:bg-paper-300 disabled:text-ink-400',
  secondary: 'bg-surface text-ink border border-paper-300 hover:bg-paper-100 disabled:text-ink-300',
  ghost: 'text-ink hover:bg-paper-200 disabled:text-ink-300',
  danger: 'bg-stop text-white hover:bg-[#a82a3d] disabled:bg-ink-300',
};
const sizes: Record<Size, string> = {
  sm: 'min-h-[36px] px-3 text-[15px]',
  md: 'min-h-[44px] px-4 text-[16px]',
  lg: 'min-h-[56px] px-6 text-[19px]',
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
}

export function Button({ variant = 'primary', size = 'md', loading, className = '', children, disabled, ...rest }: ButtonProps) {
  return (
    <button
      {...rest}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={`inline-flex items-center justify-center gap-2 rounded-md font-semibold transition-colors disabled:cursor-not-allowed ${variants[variant]} ${sizes[size]} ${className}`}
    >
      {loading && <Spinner small />}
      {children}
    </button>
  );
}

export function Spinner({ small }: { small?: boolean }) {
  const s = small ? 'h-4 w-4' : 'h-6 w-6';
  return (
    <span
      role="status"
      aria-label="Loading"
      className={`inline-block ${s} animate-spin rounded-full border-2 border-current border-t-transparent`}
    />
  );
}

export function Skeleton({ className = '' }: { className?: string }) {
  return <div className={`animate-pulse rounded-md bg-paper-200 ${className}`} aria-hidden />;
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <section className={`rounded-lg border border-paper-300/70 bg-surface p-5 ${className}`}>{children}</section>;
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-4xl">{title}</h1>
        {subtitle && <p className="mt-1 max-w-prose text-ink-500">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

export function EmptyState({ title, hint, action }: { title: string; hint?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center rounded-lg border border-dashed border-paper-300 bg-surface/60 px-6 py-12 text-center">
      <svg width="44" height="44" viewBox="0 0 44 44" fill="none" aria-hidden className="mb-3 text-ink-300">
        <path d="M6 14h32v6a3 3 0 0 0 0 6v6H6v-6a3 3 0 0 0 0-6z" stroke="currentColor" strokeWidth="2" />
        <path d="M27 14v18" stroke="currentColor" strokeWidth="2" strokeDasharray="3 3" />
      </svg>
      <p className="font-display text-2xl font-semibold">{title}</p>
      {hint && <p className="mt-1 max-w-sm text-ink-500">{hint}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function ErrorNotice({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-stop/30 bg-stop-100 px-4 py-3">
      <span>{message}</span>
      {onRetry && (
        <Button size="sm" variant="secondary" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}

const tokenTone: Record<TokenStatus, string> = {
  WAITING: 'bg-info-100 text-[#1c4aa6]',
  CALLED: 'bg-signal-100 text-[#7a5600]',
  SERVING: 'bg-go-100 text-[#0d6551]',
  COMPLETED: 'bg-paper-200 text-ink-700',
  SKIPPED: 'bg-paper-200 text-ink-500',
  CANCELLED: 'bg-stop-100 text-[#9b2236]',
  NO_SHOW: 'bg-stop-100 text-[#9b2236]',
};

export function StatusBadge({ status }: { status: TokenStatus }) {
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-sm font-semibold ${tokenTone[status]}`}>
      {statusLabel[status]}
    </span>
  );
}

const queueTone: Record<QueueStatus, string> = {
  ACTIVE: 'bg-go-100 text-[#0d6551]',
  PAUSED: 'bg-signal-100 text-[#7a5600]',
  CLOSED: 'bg-paper-200 text-ink-500',
};
const queueLabel: Record<QueueStatus, string> = { ACTIVE: 'Open', PAUSED: 'Paused', CLOSED: 'Closed' };

export function QueueBadge({ status }: { status: QueueStatus }) {
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-sm font-semibold ${queueTone[status]}`}>
      {queueLabel[status]}
    </span>
  );
}

export function ConnectionDot({ state }: { state: ConnectionState }) {
  const map = {
    online: ['bg-go', 'Live'],
    connecting: ['bg-signal', 'Connecting'],
    offline: ['bg-stop', 'Reconnecting'],
  } as const;
  const [color, text] = map[state];
  return (
    <span className="inline-flex items-center gap-1.5 text-sm text-ink-500" role="status">
      <span className={`h-2 w-2 rounded-full ${color}`} aria-hidden />
      {text}
    </span>
  );
}

export function Stat({ label, value, hint, tone = 'default' }: { label: string; value: ReactNode; hint?: string; tone?: 'default' | 'warn' | 'good' }) {
  const color = tone === 'warn' ? 'text-stop' : tone === 'good' ? 'text-go' : 'text-ink';
  return (
    <div className="rounded-lg border border-paper-300/70 bg-surface px-4 py-3">
      <div className="text-sm text-ink-500">{label}</div>
      <div className={`font-display text-4xl font-bold leading-tight ${color}`}>{value}</div>
      {hint && <div className="text-sm text-ink-400">{hint}</div>}
    </div>
  );
}

interface FieldShellProps {
  label: string;
  hint?: string;
  error?: string;
}

export const TextField = forwardRef<HTMLInputElement, FieldShellProps & InputHTMLAttributes<HTMLInputElement>>(
  function TextField({ label, hint, error, className = '', ...rest }, ref) {
    const id = useId();
    return (
      <div>
        <label htmlFor={id} className="mb-1.5 block font-display text-xl font-semibold tracking-wide">
          {label}
        </label>
        <input
          ref={ref}
          id={id}
          aria-invalid={!!error}
          aria-describedby={error || hint ? `${id}-d` : undefined}
          className={`field ${error ? 'field-error' : ''} ${className}`}
          {...rest}
        />
        {(error || hint) && (
          <p id={`${id}-d`} className={`mt-1 text-sm ${error ? 'text-stop' : 'text-ink-400'}`}>
            {error || hint}
          </p>
        )}
      </div>
    );
  },
);

export function SelectField({
  label,
  hint,
  error,
  children,
  className = '',
  ...rest
}: FieldShellProps & SelectHTMLAttributes<HTMLSelectElement>) {
  const id = useId();
  return (
    <div>
      <label htmlFor={id} className="mb-1 block text-[15px] font-semibold">
        {label}
      </label>
      <select id={id} aria-invalid={!!error} className={`field ${error ? 'field-error' : ''} ${className}`} {...rest}>
        {children}
      </select>
      {(error || hint) && <p className={`mt-1 text-sm ${error ? 'text-stop' : 'text-ink-400'}`}>{error || hint}</p>}
    </div>
  );
}

export function TextAreaField({ label, hint, error, className = '', ...rest }: FieldShellProps & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const id = useId();
  return (
    <div>
      <label htmlFor={id} className="mb-1 block text-[15px] font-semibold">
        {label}
      </label>
      <textarea id={id} className={`field ${error ? 'field-error' : ''} ${className}`} {...rest} />
      {(error || hint) && <p className={`mt-1 text-sm ${error ? 'text-stop' : 'text-ink-400'}`}>{error || hint}</p>}
    </div>
  );
}

export function Toggle({ label, checked, onChange, hint }: { label: string; checked: boolean; onChange: (v: boolean) => void; hint?: string }) {
  const id = useId();
  return (
    <div className="flex items-start justify-between gap-4">
      <label htmlFor={id} className="cursor-pointer">
        <span className="block text-[15px] font-semibold">{label}</span>
        {hint && <span className="block text-sm text-ink-400">{hint}</span>}
      </label>
      <button
        id={id}
        type="button"
        role="switch"
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        className={`relative mt-0.5 h-7 w-12 shrink-0 rounded-full transition-colors ${checked ? 'bg-go' : 'bg-paper-300'}`}
      >
        <span className={`absolute top-0.5 h-6 w-6 rounded-full bg-surface shadow transition-all ${checked ? 'left-[22px]' : 'left-0.5'}`} />
      </button>
    </div>
  );
}

export function Pagination({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (p: number) => void }) {
  if (totalPages <= 1) return null;
  return (
    <div className="mt-4 flex items-center justify-between">
      <Button size="sm" variant="secondary" disabled={page <= 0} onClick={() => onPage(page - 1)}>
        Previous
      </Button>
      <span className="text-sm text-ink-500">
        Page {page + 1} of {totalPages}
      </span>
      <Button size="sm" variant="secondary" disabled={page >= totalPages - 1} onClick={() => onPage(page + 1)}>
        Next
      </Button>
    </div>
  );
}

export function TableWrap({ children }: { children: ReactNode }) {
  return <div className="overflow-x-auto rounded-lg border border-paper-300/70 bg-surface">{children}</div>;
}
