import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { Button, TextField } from '../components/ui';
import { useAuth } from '../hooks/useAuth';
import { HttpError } from '../services/http';
import type { Role } from '../types/api';

const homeFor = (role: Role) => (role === 'ADMIN' ? '/admin' : role === 'STAFF' ? '/staff' : '/token');

function Shell({ title, note, children }: { title: string; note: string; children: React.ReactNode }) {
  return (
    <div className="mx-auto grid max-w-4xl gap-10 px-4 py-12 md:grid-cols-[1fr_1.1fr] md:py-20">
      <div>
        <h1 className="text-5xl">{title}</h1>
        <p className="mt-3 max-w-xs text-lg text-ink-500">{note}</p>
      </div>
      <div className="md:pt-3">{children}</div>
    </div>
  );
}

export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [id, setId] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={homeFor(user.role)} replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const u = await login(id, password);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from && from !== '/login' ? from : homeFor(u.role), { replace: true });
    } catch (err) {
      setError(err instanceof HttpError ? err.message : 'Something went wrong. Please try again.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <Shell title="Sign in" note="Staff and admins use this to open the counter desk. Customers can sign in to see their token history.">
      <form onSubmit={submit} className="grid gap-4" noValidate>
        <TextField label="Email or username" autoComplete="username" value={id} onChange={(e) => setId(e.target.value)} required />
        <TextField label="Password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        {error && (
          <p role="alert" className="rounded-md bg-stop-100 px-3 py-2 text-[15px]">
            {error}
          </p>
        )}
        <Button type="submit" size="lg" loading={busy} disabled={!id || !password}>
          Sign in
        </Button>
        <p className="text-center text-ink-500">
          New customer?{' '}
          <Link to="/register" className="font-semibold text-ink underline underline-offset-4">
            Create an account
          </Link>
        </p>
      </form>
    </Shell>
  );
}

export function RegisterPage() {
  const { user, register } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [fullName, setFullName] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState('');
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={homeFor(user.role)} replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setErrors({});
    setFormError('');
    try {
      await register(email, fullName, password);
      navigate('/token', { replace: true });
    } catch (err) {
      if (err instanceof HttpError && err.fieldErrors) setErrors(err.fieldErrors);
      else setFormError(err instanceof HttpError ? err.message : 'Something went wrong. Please try again.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <Shell title="Create an account" note="Optional. You can join a queue as a guest without it.">
      <form onSubmit={submit} className="grid gap-4" noValidate>
        <TextField label="Full name" autoComplete="name" value={fullName} error={errors.fullName} onChange={(e) => setFullName(e.target.value)} required />
        <TextField label="Email" type="email" autoComplete="email" value={email} error={errors.email} onChange={(e) => setEmail(e.target.value)} required />
        <TextField
          label="Password"
          type="password"
          autoComplete="new-password"
          value={password}
          error={errors.password}
          hint="At least 10 characters with upper and lower case letters, a number and a symbol."
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        {formError && (
          <p role="alert" className="rounded-md bg-stop-100 px-3 py-2 text-[15px]">
            {formError}
          </p>
        )}
        <Button type="submit" size="lg" loading={busy} disabled={!email || !fullName || !password}>
          Create account
        </Button>
        <p className="text-center text-sm text-ink-500">
          By creating an account you accept the{' '}
          <Link to="/terms" className="underline underline-offset-4">
            Terms
          </Link>{' '}
          and{' '}
          <Link to="/privacy" className="underline underline-offset-4">
            Privacy
          </Link>{' '}
          notice.
        </p>
        <p className="text-center text-ink-500">
          Already registered?{' '}
          <Link to="/login" className="font-semibold text-ink underline underline-offset-4">
            Sign in
          </Link>
        </p>
      </form>
    </Shell>
  );
}

export function NotFoundPage() {
  return (
    <div className="mx-auto max-w-md px-4 py-20 text-center">
      <h1 className="text-6xl">Page not found</h1>
      <p className="mt-2 text-ink-500">The page you are looking for does not exist or has moved.</p>
      <Link to="/" className="mt-6 inline-block">
        <Button>Back to home</Button>
      </Link>
    </div>
  );
}
