import { Link, NavLink, Outlet } from 'react-router-dom';
import { Logo } from '../components/Logo';
import { useAuth } from '../hooks/useAuth';

const linkClass = ({ isActive }: { isActive: boolean }) =>
  `rounded-md px-3 py-2 text-[16px] font-medium hover:bg-paper-200 ${isActive ? 'bg-paper-200' : ''}`;

export default function PublicLayout() {
  const { user, logout } = useAuth();
  const home = user?.role === 'ADMIN' ? '/admin' : user?.role === 'STAFF' ? '/staff' : null;
  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b border-paper-300/70 bg-paper-100">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-2 px-4 py-3">
          <Link to="/" aria-label="SmartQueue home">
            <Logo />
          </Link>
          <nav className="flex flex-wrap items-center gap-1" aria-label="Main">
            <NavLink to="/join" className={linkClass}>
              Join a queue
            </NavLink>
            <NavLink to="/token" className={linkClass}>
              My token
            </NavLink>
            {home && (
              <NavLink to={home} className={linkClass}>
                {user?.role === 'ADMIN' ? 'Admin' : 'Staff desk'}
              </NavLink>
            )}
            {user ? (
              <button onClick={() => void logout()} className="rounded-md px-3 py-2 text-[16px] font-medium hover:bg-paper-200">
                Sign out
              </button>
            ) : (
              <NavLink to="/login" className={linkClass}>
                Sign in
              </NavLink>
            )}
          </nav>
        </div>
      </header>
      <main className="flex-1">
        <Outlet />
      </main>
      <footer className="border-t border-paper-300/70 bg-paper-100 py-6 text-sm text-ink-500">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-2 px-4">
          <Logo />
          <p>Wait times are estimates and can change as the queue moves.</p>
          <nav className="flex gap-4" aria-label="Legal">
            <Link to="/privacy" className="underline underline-offset-4">
              Privacy
            </Link>
            <Link to="/terms" className="underline underline-offset-4">
              Terms
            </Link>
          </nav>
        </div>
      </footer>
    </div>
  );
}
