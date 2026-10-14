import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpError, get, tokenStore } from '../services/http';

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

describe('http client', () => {
  const fetchMock = vi.fn();
  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    fetchMock.mockReset();
  });
  afterEach(() => vi.unstubAllGlobals());

  it('turns an API error body into an HttpError with the stable code', async () => {
    fetchMock.mockResolvedValueOnce(json(409, { code: 'QUEUE_FULL', message: 'This queue is full.' }));
    await expect(get('/api/x', { auth: false })).rejects.toMatchObject({ status: 409, code: 'QUEUE_FULL', message: 'This queue is full.' });
  });

  it('keeps field errors from validation responses', async () => {
    fetchMock.mockResolvedValueOnce(json(400, { code: 'VALIDATION_ERROR', message: 'Validation failed.', fieldErrors: { email: 'must be valid' } }));
    const err = (await get('/api/x', { auth: false }).catch((e) => e)) as HttpError;
    expect(err.fieldErrors).toEqual({ email: 'must be valid' });
  });

  it('reports network failures with a readable message', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    await expect(get('/api/x', { auth: false })).rejects.toMatchObject({ code: 'NETWORK_ERROR' });
  });

  it('refreshes the access token once on 401 and retries the request', async () => {
    tokenStore.set({ accessToken: 'old', refreshToken: 'r1' });
    fetchMock
      .mockResolvedValueOnce(json(401, { code: 'UNAUTHORIZED', message: 'x' }))
      .mockResolvedValueOnce(json(200, { accessToken: 'new', refreshToken: 'r2', expiresInSeconds: 900, user: {} }))
      .mockResolvedValueOnce(json(200, { ok: true }));

    const result = await get<{ ok: boolean }>('/api/secure');
    expect(result.ok).toBe(true);
    expect(tokenStore.access()).toBe('new');
    expect(tokenStore.refresh()).toBe('r2');
    const retryHeaders = fetchMock.mock.calls[2][1].headers as Record<string, string>;
    expect(retryHeaders.Authorization).toBe('Bearer new');
  });

  it('clears the session when the refresh token is rejected', async () => {
    tokenStore.set({ accessToken: 'old', refreshToken: 'bad' });
    fetchMock
      .mockResolvedValueOnce(json(401, { code: 'UNAUTHORIZED', message: 'x' }))
      .mockResolvedValueOnce(json(401, { code: 'INVALID_REFRESH_TOKEN', message: 'x' }));
    await expect(get('/api/secure')).rejects.toMatchObject({ status: 401 });
    expect(tokenStore.access()).toBeNull();
  });
});
