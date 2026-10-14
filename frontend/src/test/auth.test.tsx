import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { RequireRole } from '../components/RequireRole';
import { AuthProvider } from '../hooks/useAuth';
import { ToastProvider } from '../hooks/useToast';
import { LoginPage } from '../pages/AuthPages';
import { HttpError, tokenStore } from '../services/http';
import type { AuthResponse } from '../types/api';

const authMock = vi.hoisted(() => ({ login: vi.fn(), register: vi.fn(), logout: vi.fn(), me: vi.fn() }));
vi.mock('../services/api', () => ({ authApi: authMock }));

const staffAuth: AuthResponse = {
  accessToken: 'a',
  refreshToken: 'r',
  expiresInSeconds: 900,
  user: { id: 'u1', email: 's@x.test', username: 'staff1', fullName: 'Sam Staff', role: 'STAFF', status: 'ACTIVE', organizationId: null },
};

function app(initial: string) {
  return render(
    <MemoryRouter initialEntries={[initial]}>
      <ToastProvider>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/" element={<p>Home page</p>} />
            <Route element={<RequireRole roles={['STAFF', 'ADMIN']} />}>
              <Route path="/staff" element={<p>Staff desk</p>} />
            </Route>
            <Route element={<RequireRole roles={['ADMIN']} />}>
              <Route path="/admin" element={<p>Admin area</p>} />
            </Route>
          </Routes>
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  );
}

describe('authentication', () => {
  beforeEach(() => {
    Object.values(authMock).forEach((m) => m.mockReset());
  });

  it('signs a staff member in and sends them to the staff desk', async () => {
    authMock.login.mockResolvedValue(staffAuth);
    const user = userEvent.setup();
    app('/login');
    await user.type(screen.getByLabelText('Email or username'), 'staff1');
    await user.type(screen.getByLabelText('Password'), 'secret');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Staff desk')).toBeInTheDocument();
    expect(authMock.login).toHaveBeenCalledWith('staff1', 'secret');
    expect(tokenStore.access()).toBe('a');
  });

  it('shows the server message for bad credentials and stays on the page', async () => {
    authMock.login.mockRejectedValue(new HttpError(401, { code: 'INVALID_CREDENTIALS', message: 'Invalid credentials.' }));
    const user = userEvent.setup();
    app('/login');
    await user.type(screen.getByLabelText('Email or username'), 'staff1');
    await user.type(screen.getByLabelText('Password'), 'wrong');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid credentials.');
  });

  it('redirects anonymous visitors from protected routes to sign in', async () => {
    app('/staff');
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('keeps staff out of the admin area', async () => {
    tokenStore.set({ accessToken: 'a', refreshToken: 'r' });
    authMock.me.mockResolvedValue(staffAuth.user);
    app('/admin');
    await waitFor(() => expect(screen.getByText('Home page')).toBeInTheDocument());
    expect(screen.queryByText('Admin area')).not.toBeInTheDocument();
  });
});
