import { Link, Outlet } from 'react-router-dom';
import { Logo } from '../components/Logo';
import { useAuth } from '../hooks/useAuth';

export default function StaffLayout() {
  const { user, logout, hasRole } = useAuth();
  return (
    <div className="min-h-screen bg-paper">
      <header className="bg-ink text-white">
        <div className="mx-auto flex max-w-7xl items-center justify-between gap-3 px-4 py-3">
          <Link to="/staff" aria-label="Staff desk home">
            <Logo light />
          </Link>
          <div className="flex items-center gap-2 text-[15px]">
            <span className="hidden text-ink-300 sm:inline">{user?.fullName}</span>
            {hasRole('ADMIN') && (
              <Link to="/admin" className="rounded-md px-3 py-2 hover:bg-ink-700">
                Admin
              </Link>
            )}
            <button onClick={() => void logout()} className="rounded-md px-3 py-2 hover:bg-ink-700">
              Sign out
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-7xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  );
}
