import { API_URL, get, guestSession, post, put, tokenStore } from './http';
import type {
  AdminQueue,
  AdminTokenRow,
  AdminUser,
  AnalyticsReport,
  AuditRow,
  AuthResponse,
  Branch,
  Counter,
  DashboardStats,
  Department,
  JoinResponse,
  Organization,
  Page,
  QueueConfig,
  QueueSnapshot,
  ServiceItem,
  Settings,
  StaffQueueState,
  StaffQueueSummary,
  StaffToken,
  TokenView,
  User,
} from '../types/api';

const qs = (params: Record<string, string | number | undefined | null>) => {
  const p = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') p.set(k, String(v));
  });
  const s = p.toString();
  return s ? `?${s}` : '';
};

export const authApi = {
  login: (login: string, password: string) =>
    post<AuthResponse>('/api/auth/login', { login, password }, { auth: false }),
  register: (email: string, fullName: string, password: string) =>
    post<AuthResponse>('/api/auth/register', { email, fullName, password }, { auth: false }),
  logout: (refreshToken: string) => post<void>('/api/auth/logout', { refreshToken }, { auth: false }),
  me: () => get<User>('/api/auth/me'),
};

export const publicApi = {
  organizations: () => get<Organization[]>('/api/public/organizations', { auth: false }),
  branches: (orgId: string) =>
    get<Branch[]>(`/api/public/organizations/${orgId}/branches`, { auth: false }),
  branchQueues: (branchId: string) =>
    get<QueueSnapshot[]>(`/api/public/branches/${branchId}/queues`, { auth: false }),
  queue: (publicRef: string) => get<QueueSnapshot>(`/api/public/queues/${publicRef}`, { auth: false }),
  qrUrl: (publicRef: string, opts: { size?: number; download?: boolean } = {}) =>
    `${API_URL}/api/public/queues/${publicRef}/qr${qs({ size: opts.size, download: opts.download ? 'true' : undefined })}`,
};

export const tokenApi = {
  /** Joins a queue. Guests are identified by a server-issued session id kept in local storage. */
  async join(queueId: string, customerName?: string): Promise<JoinResponse> {
    const headers: Record<string, string> = {};
    const session = guestSession.get();
    if (session && !tokenStore.access()) headers['X-Session-Id'] = session;
    const res = await post<JoinResponse>(
      `/api/queues/${queueId}/join`,
      customerName ? { customerName } : {},
      { headers, auth: !!tokenStore.access() },
    );
    if (res.sessionId) guestSession.set(res.sessionId);
    return res;
  },
  byReference: (reference: string) => get<TokenView>(`/api/tokens/${reference}`, { auth: false }),
  cancel: (reference: string) =>
    post<TokenView>(`/api/public/tokens/${reference}/cancel`, undefined, { auth: false }),
  mine: () => get<TokenView[]>('/api/me/tokens'),
};

export const staffApi = {
  queues: () => get<StaffQueueSummary[]>('/api/staff/queues'),
  state: (queueId: string) => get<StaffQueueState>(`/api/staff/queues/${queueId}/state`),
  next: (queueId: string, counterId?: string | null) =>
    post<StaffToken>(`/api/queues/${queueId}/next`, counterId ? { counterId } : {}),
  pause: (queueId: string) => post<QueueSnapshot>(`/api/queues/${queueId}/pause`),
  resume: (queueId: string) => post<QueueSnapshot>(`/api/queues/${queueId}/resume`),
  action: (tokenId: string, action: 'recall' | 'serving' | 'complete' | 'skip' | 'no-show' | 'cancel' | 'requeue') =>
    post<StaffToken>(`/api/tokens/${tokenId}/${action}`),
  move: (tokenId: string, position: number) =>
    post<StaffToken>(`/api/tokens/${tokenId}/move`, { position }),
  walkIn: (queueId: string, priority: boolean, customerName?: string) =>
    post<JoinResponse>(`/api/queues/${queueId}/join`, { priority, customerName: customerName || undefined }),
};

export interface ResourceBody {
  [key: string]: unknown;
}

export const adminApi = {
  dashboard: () => get<DashboardStats>('/api/admin/dashboard'),
  analytics: (p: { from?: string; to?: string; queueId?: string }) =>
    get<AnalyticsReport>(`/api/admin/analytics${qs(p)}`),

  organizations: () => get<Organization[]>('/api/admin/organizations'),
  saveOrganization: (id: string | null, body: ResourceBody) =>
    id ? put<Organization>(`/api/admin/organizations/${id}`, body) : post<Organization>('/api/admin/organizations', body),

  branches: () => get<Branch[]>('/api/admin/branches'),
  saveBranch: (id: string | null, body: ResourceBody) =>
    id ? put<Branch>(`/api/admin/branches/${id}`, body) : post<Branch>('/api/admin/branches', body),

  departments: () => get<Department[]>('/api/admin/departments'),
  saveDepartment: (id: string | null, body: ResourceBody) =>
    id ? put<Department>(`/api/admin/departments/${id}`, body) : post<Department>('/api/admin/departments', body),

  services: () => get<ServiceItem[]>('/api/admin/services'),
  saveService: (id: string | null, body: ResourceBody) =>
    id ? put<ServiceItem>(`/api/admin/services/${id}`, body) : post<ServiceItem>('/api/admin/services', body),

  queues: () => get<AdminQueue[]>('/api/admin/queues'),
  createQueue: (body: ResourceBody) => post<AdminQueue>('/api/admin/queues', body),
  updateQueue: (id: string, body: ResourceBody) => put<AdminQueue>(`/api/admin/queues/${id}`, body),
  updateQueueConfig: (id: string, body: QueueConfig) =>
    put<AdminQueue>(`/api/admin/queues/${id}/config`, body as unknown as ResourceBody),

  counters: (queueId: string) => get<Counter[]>(`/api/admin/queues/${queueId}/counters`),
  saveCounter: (queueId: string, id: string | null, body: ResourceBody) =>
    id
      ? put<Counter>(`/api/admin/queues/${queueId}/counters/${id}`, body)
      : post<Counter>(`/api/admin/queues/${queueId}/counters`, body),

  staff: () => get<AdminUser[]>('/api/admin/staff'),
  createStaff: (body: ResourceBody) => post<AdminUser>('/api/admin/staff', body),
  updateUser: (id: string, body: ResourceBody) => put<AdminUser>(`/api/admin/users/${id}`, body),
  resetPassword: (id: string, newPassword: string) =>
    post<void>(`/api/admin/users/${id}/reset-password`, { newPassword }),
  customers: (page: number, size = 20) =>
    get<Page<AdminUser>>(`/api/admin/customers${qs({ page, size })}`),

  tokens: (p: { queueId?: string; status?: string; date?: string; page: number; size?: number }) =>
    get<Page<AdminTokenRow>>(`/api/admin/tokens${qs(p)}`),
  auditLogs: (p: { action?: string; entityType?: string; from?: string; to?: string; page: number; size?: number }) =>
    get<Page<AuditRow>>(`/api/admin/audit-logs${qs(p)}`),
  settings: () => get<Settings>('/api/admin/settings'),
};
