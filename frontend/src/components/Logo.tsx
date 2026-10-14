export function LogoMark({ size = 32 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden>
      <rect width="32" height="32" rx="7" fill="#0B2545" />
      <path d="M8 11h16v3.2a2.3 2.3 0 0 0 0 4.6V22H8v-3.2a2.3 2.3 0 0 0 0-4.6z" fill="#FFB703" />
      <path d="M19 11v11" stroke="#0B2545" strokeWidth="1.6" strokeDasharray="1.6 2" />
    </svg>
  );
}

export function Logo({ light }: { light?: boolean }) {
  return (
    <span className={`inline-flex items-center gap-2 font-display text-2xl font-bold ${light ? 'text-white' : 'text-ink'}`}>
      <LogoMark />
      SmartQueue
    </span>
  );
}
