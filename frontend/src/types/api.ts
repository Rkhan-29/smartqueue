// Types mirror the backend DTOs. Business rules live on the server; the UI only renders these.

export type Role = 'CUSTOMER' | 'STAFF' | 'ADMIN';
export type UserStatus = 'ACTIVE' | 'DISABLED';
export type QueueStatus = 'ACTIVE' | 'PAUSED' | 'CLOSED';
export type TokenStatus =
  | 'WAITING'
  | 'CALLED'
  | 'SERVING'
  | 'COMPLETED'
  | 'SKIPPED'
  | 'CANCELLED'
  | 'NO_SHOW';
export type PriorityMode = 'DISABLED' | 'ALWAYS_FIRST' | 'AFTER_N';
export type EstimateConfidence = 'LOW' | 'MEDIUM' | 'HIGH';

export interface ApiError {
  code: string;
  message: string;
  timestamp?: string;
  path?: string;
  fieldErrors?: Record<string, string>;
}

export interface User {
  id: string;
  email: string;
  username: string;
  fullName: string;
  role: Role;
  status: UserStatus;
  organizationId: string | null;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  user: User;
}

export interface ServingEntry {
  tokenNumber: string;
  counterName: string | null;
  status: TokenStatus;
}

export interface QueueSnapshot {
  queueId: string;
  publicRef: string;
  name: string;
  status: QueueStatus;
  serviceName: string;
  departmentName: string;
  branchName: string;
  organizationName: string;
  waitingCount: number;
  nowServing: ServingEntry[];
  lastCalledToken: string | null;
  avgServiceMinutes: number;
  estimatedWaitForNewTokenMinutes: number;
  confidence: EstimateConfidence;
  joinable: boolean;
  unavailableReason: string | null;
  priorityEnabled: boolean;
  updatedAt: string;
}

export interface TokenView {
  reference: string;
  tokenNumber: string;
  status: TokenStatus;
  priority: boolean;
  queueId: string;
  queuePublicRef: string;
  queueName: string;
  queueStatus: QueueStatus;
  serviceName: string;
  branchName: string;
  organizationName: string;
  currentlyServing: string | null;
  peopleAhead: number;
  position: number;
  estimatedWaitMinutes: number | null;
  confidence: EstimateConfidence | null;
  counterName: string | null;
  nearTurn: boolean;
  message: string;
  joinedAt: string;
  calledAt: string | null;
  updatedAt: string;
  serverTime: string;
}

export interface JoinResponse {
  token: TokenView;
  sessionId: string | null;
}

export interface StaffToken {
  id: string;
  tokenNumber: string;
  status: TokenStatus;
  priority: boolean;
  customerName: string | null;
  counterId: string | null;
  counterName: string | null;
  createdAt: string;
  calledAt: string | null;
  servingAt: string | null;
  completedAt: string | null;
  waitingSeconds: number;
}

export interface QueueStats {
  waiting: number;
  called: number;
  serving: number;
  completedToday: number;
  cancelledToday: number;
  skippedToday: number;
  noShowToday: number;
  totalToday: number;
  avgWaitMinutes: number;
  avgServiceMinutes: number;
}

export interface Counter {
  id: string;
  name: string;
  active: boolean;
  currentToken: string | null;
}

export interface StaffQueueState {
  queue: QueueSnapshot;
  stats: QueueStats;
  active: StaffToken[];
  waiting: StaffToken[];
  recent: StaffToken[];
  nextToken: StaffToken | null;
  counters: Counter[];
}

export interface StaffQueueSummary {
  queueId: string;
  publicRef: string;
  name: string;
  status: QueueStatus;
  serviceName: string;
  branchName: string;
  waitingCount: number;
}

export interface QueueEvent {
  type: string;
  queue: QueueSnapshot;
  tokenNumber: string | null;
  at: string;
}

export interface TokenEvent {
  type: string;
  token: TokenView;
  title: string | null;
  message: string;
  at: string;
}

// ---- Admin ----
export interface Organization {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  active: boolean;
}
export interface Branch {
  id: string;
  organizationId: string;
  organizationName: string;
  name: string;
  address: string | null;
  city: string | null;
  timeZone: string;
  active: boolean;
}
export interface Department {
  id: string;
  branchId: string;
  branchName: string;
  name: string;
  active: boolean;
}
export interface ServiceItem {
  id: string;
  departmentId: string;
  departmentName: string;
  branchName: string;
  name: string;
  description: string | null;
  active: boolean;
}

export interface QueueConfig {
  tokenPrefix: string;
  priorityPrefix: string;
  startingNumber: number;
  numberPadding: number;
  maxQueueSize: number;
  priorityMode: PriorityMode;
  priorityAfterN: number;
  avgServiceMinutes: number;
  openingTime: string | null;
  closingTime: string | null;
  breakStart: string | null;
  breakEnd: string | null;
  autoExpireMinutes: number;
  noShowTimeoutMinutes: number;
  allowDuplicateActive: boolean;
  directServing: boolean;
  nearTurnThreshold: number;
}

export interface AdminQueue {
  id: string;
  publicRef: string;
  name: string;
  status: QueueStatus;
  active: boolean;
  serviceId: string;
  serviceName: string;
  departmentName: string;
  branchName: string;
  organizationName: string;
  config: QueueConfig;
  waitingCount: number;
  joinUrl: string;
}

export interface AdminUser {
  id: string;
  email: string;
  username: string;
  fullName: string;
  role: Role;
  status: UserStatus;
  organizationId: string | null;
  lastLoginAt: string | null;
  queueIds: string[];
  createdAt: string;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface AdminTokenRow {
  id: string;
  tokenNumber: string;
  status: TokenStatus;
  priority: boolean;
  queueId: string;
  queueName: string;
  customerName: string | null;
  guest: boolean;
  serviceDate: string;
  createdAt: string;
  calledAt: string | null;
  completedAt: string | null;
}

export interface AuditRow {
  id: string;
  actor: string;
  action: string;
  entityType: string;
  entityId: string | null;
  ipAddress: string | null;
  metadata: string | null;
  createdAt: string;
}

export interface HourBucket {
  hour: number;
  tokens: number;
}
export interface QueueLoad {
  queueId: string;
  name: string;
  waiting: number;
  inProgress: number;
  completedToday: number;
  avgWaitMinutes: number;
  avgServiceMinutes: number;
  utilizationPercent: number;
}
export interface DashboardStats {
  totalTokensToday: number;
  completedToday: number;
  waitingNow: number;
  cancelledToday: number;
  noShowToday: number;
  avgWaitMinutes: number;
  avgServiceMinutes: number;
  queueUtilizationPercent: number;
  activeQueues: number;
  peakHours: HourBucket[];
  queues: QueueLoad[];
}
export interface DayPoint {
  date: string;
  total: number;
  completed: number;
  cancelled: number;
  noShow: number;
  avgWaitMinutes: number;
}
export interface QueueRow {
  queueId: string;
  name: string;
  total: number;
  completed: number;
  cancelled: number;
  avgWaitMinutes: number;
  avgServiceMinutes: number;
}
export interface AnalyticsReport {
  from: string;
  to: string;
  daily: DayPoint[];
  hourly: HourBucket[];
  byQueue: QueueRow[];
}

export interface Settings {
  publicBaseUrl: string;
  accessTokenTtlMinutes: number;
  refreshTokenTtlDays: number;
  loginMaxAttempts: number;
  loginLockoutMinutes: number;
  authRateLimitPerMinute: number;
  joinRateLimitPerMinute: number;
  demoDataEnabled: boolean;
}
