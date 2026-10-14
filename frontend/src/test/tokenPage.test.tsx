import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import TokenPage from '../pages/TokenPage';
import { ToastProvider } from '../hooks/useToast';
import { HttpError } from '../services/http';
import type { TokenEvent, TokenView } from '../types/api';

const handlers: { onMessage?: (e: TokenEvent) => void } = {};

vi.mock('../services/realtime', () => ({
  tokenTopic: (r: string) => `/topic/token/${r}`,
  queueTopic: (r: string) => `/topic/queue/${r}`,
  subscribeTopic: (_dest: string, h: { onMessage: (e: TokenEvent) => void; onState?: (s: string) => void; onConnect?: () => void }) => {
    handlers.onMessage = h.onMessage;
    h.onState?.('online');
    return () => undefined;
  },
}));

const apiMock = vi.hoisted(() => ({ byReference: vi.fn(), cancel: vi.fn() }));
vi.mock('../services/api', () => ({ tokenApi: apiMock }));

const base: TokenView = {
  reference: 'ref123',
  tokenNumber: 'A023',
  status: 'WAITING',
  priority: false,
  queueId: 'q1',
  queuePublicRef: 'pubref',
  queueName: 'General OPD',
  queueStatus: 'ACTIVE',
  serviceName: 'General Consultation',
  branchName: 'Mumbai Central',
  organizationName: 'SmartCare Hospital',
  currentlyServing: 'A018',
  peopleAhead: 4,
  position: 5,
  estimatedWaitMinutes: 12,
  confidence: 'LOW',
  counterName: null,
  nearTurn: false,
  message: "You're in line. We'll update this page automatically.",
  joinedAt: '2026-10-10T08:00:00Z',
  calledAt: null,
  updatedAt: '2026-10-10T08:05:00Z',
  serverTime: '2026-10-10T08:05:00Z',
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/queue/token/ref123']}>
      <ToastProvider>
        <Routes>
          <Route path="/queue/token/:reference" element={<TokenPage />} />
        </Routes>
      </ToastProvider>
    </MemoryRouter>,
  );
}

describe('TokenPage', () => {
  beforeEach(() => {
    apiMock.byReference.mockReset();
    apiMock.cancel.mockReset();
  });

  it('shows token, people ahead, estimate and who is being served', async () => {
    apiMock.byReference.mockResolvedValue(base);
    renderPage();
    expect(await screen.findByLabelText('Token A023')).toBeInTheDocument();
    expect(screen.getByText('4')).toBeInTheDocument();
    expect(screen.getAllByText('~12 min').length).toBeGreaterThan(0);
    expect(screen.getByText('A018')).toBeInTheDocument();
    expect(screen.getByText(/estimate, not a promise/i)).toBeInTheDocument();
  });

  it('updates in place when the server pushes a call event', async () => {
    apiMock.byReference.mockResolvedValue(base);
    renderPage();
    await screen.findByLabelText('Token A023');

    const called: TokenView = { ...base, status: 'CALLED', counterName: 'Counter 3', peopleAhead: 0, message: 'PLEASE PROCEED TO COUNTER 3' };
    act(() => handlers.onMessage?.({ type: 'CALLED', token: called, title: "It's your turn", message: called.message, at: '2026-10-10T08:10:00Z' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Please proceed to Counter 3');
    expect(screen.getAllByText('PLEASE PROCEED TO COUNTER 3').length).toBeGreaterThan(0);
  });

  it('asks for confirmation before leaving the queue, then shows it cancelled', async () => {
    apiMock.byReference.mockResolvedValue(base);
    apiMock.cancel.mockResolvedValue({ ...base, status: 'CANCELLED', message: 'This token was cancelled.' });
    const user = userEvent.setup();
    renderPage();
    await screen.findByLabelText('Token A023');

    await user.click(screen.getByRole('button', { name: 'Leave the queue' }));
    expect(apiMock.cancel).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Leave queue' }));

    await waitFor(() => expect(apiMock.cancel).toHaveBeenCalledWith('ref123'));
    expect(await screen.findByText('This token was cancelled.')).toBeInTheDocument();
  });

  it('shows a clear message when the token does not exist', async () => {
    apiMock.byReference.mockRejectedValue(new HttpError(404, { code: 'TOKEN_NOT_FOUND', message: 'Token not found.' }));
    renderPage();
    expect(await screen.findByRole('heading', { name: 'Token not found' })).toBeInTheDocument();
  });
});
