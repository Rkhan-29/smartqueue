import { useState } from 'react';
import { Link, NavLink, Outlet } from 'react-router-dom';
import { Logo } from '../components/Logo';
import { useAuth } from '../hooks/useAuth';

const nav = [
  { to: '/admin', label: 'Dashboard', end: true },
  { to: '/admin/organizations', label: 'Organizations' },
  { to: '/admin/branches', label: 'Branches' },
  { to: '/admin/departments', label: 'Departments' },
  { to: '/admin/services', label: 'Services' },
  { to: '/admin/queues', label: 'Queues' },
  { to: '/admin/counters', label: 'Counters' },
  { to: '/admin/staff', label: 'Staff' },
  { to: '/admin/customers', label: 'Customers' },
  { to: '/admin/tokens', label: 'Tokens' },
  { to: '/admin/analytics', label: 'Analytics' },
  { to: '/admin/audit', label: 'Audit logs' },
  { to: '/admin/settings', label: 'Settings' },
];

export default function AdminLayout() {
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);

  const links = (
    <nav aria-label="Admin" className="grid gap-0.5 p-3">
      {nav.map((n) => (
        <NavLink
          key={n.to}
          to={n.to}
          end={n.end}
          onClick={() => setOpen(false)}
          className={({ isActive }) =>
            `rounded-md px-3 py-2 text-[16px] font-medium ${isActive ? 'bg-ink-700 text-signal' : 'text-white/90 hover:bg-ink-700'}`
          }
        >
          {n.label}
        </NavLink>
      ))}
    </nav>
  );

  return (
    <div className="min-h-screen bg-paper lg:grid lg:grid-cols-[236px_1fr]">
      <aside className="no-print hidden bg-ink lg:block">
        <div className="sticky top-0 flex h-screen flex-col">
          <div className="px-5 py-5">
            <Link to="/admin">
              <Logo light />
            </Link>
          </div>
          <div className="flex-1 overflow-y-auto">{links}</div>
          <div className="border-t border-ink-700 p-4 text-[15px] text-white/80">
            <p className="truncate font-semibold text-white">{user?.fullName}</p>
            <div className="mt-2 flex gap-3">
              <Link to="/staff" className="underline underline-offset-4">
                Staff desk
              </Link>
              <button onClick={() => void logout()} className="underline underline-offset-4">
                Sign out
              </button>
            </div>
          </div>
        </div>
      </aside>

      <div className="min-w-0">
        <header className="no-print flex items-center justify-between bg-ink px-4 py-3 text-white lg:hidden">
          <Logo light />
          <button
            aria-expanded={open}
            aria-controls="admin-drawer"
            onClick={() => setOpen((v) => !v)}
            className="rounded-md px-3 py-2 font-semibold hover:bg-ink-700"
          >
            {open ? 'Close' : 'Menu'}
          </button>
        </header>
        {open && (
          <div id="admin-drawer" className="no-print bg-ink lg:hidden">
            {links}
            <div className="flex gap-4 border-t border-ink-700 p-4 text-white/80">
              <Link to="/staff" className="underline underline-offset-4">
                Staff desk
              </Link>
              <button onClick={() => void logout()} className="underline underline-offset-4">
                Sign out
              </button>
            </div>
          </div>
        )}
        <main className="mx-auto max-w-6xl px-4 py-6 lg:px-8 lg:py-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
