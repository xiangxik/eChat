import { readRuntimeEnv } from '../runtimeEnv';

export const apiBaseUrl = readRuntimeEnv('VITE_API_BASE_URL') ?? '';

export interface HealthResponse {
  status: string;
  service: string;
  version: string;
}

export class ApiRequestError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(message: string, status: number, code?: string) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.code = code;
  }
}

interface ApiErrorPayload {
  code?: string;
  message?: string;
  detail?: string;
  error?: string;
}

export interface AdminSessionResponse {
  authenticated: boolean;
  actorId?: string;
  displayName?: string;
  tenantId?: string;
  roles?: string[];
  superAdmin?: boolean;
}

export interface AdminLoginRequest {
  username?: string;
  password: string;
}

export async function apiRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);

  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  const selectedTenantId = readSelectedAdminTenantId();
  const tenantScopedRequest = (path.startsWith('/api/admin/') && !path.startsWith('/api/admin/auth/')) || path.startsWith('/api/chat/');
  if (selectedTenantId && tenantScopedRequest && !headers.has('X-Tenant-Id')) {
    headers.set('X-Tenant-Id', selectedTenantId);
  }

  const response = await fetch(`${apiBaseUrl}${path}`, { ...options, headers, credentials: 'include' });

  if (!response.ok) {
    throw await toApiRequestError(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export function loginAdmin(password: string, username?: string) {
  const request: AdminLoginRequest = username ? { username, password } : { password };
  return apiRequest<AdminSessionResponse>('/api/admin/auth/login', {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export function logoutAdmin() {
  return apiRequest<void>('/api/admin/auth/logout', { method: 'POST' });
}

export function fetchAdminSession() {
  return apiRequest<AdminSessionResponse>('/api/admin/auth/session');
}

const adminTenantStorageKey = 'echat.admin.selectedTenantId';

export function readSelectedAdminTenantId() {
  try {
    return window.localStorage.getItem(adminTenantStorageKey) ?? undefined;
  } catch {
    return undefined;
  }
}

export function writeSelectedAdminTenantId(tenantId: string) {
  try {
    window.localStorage.setItem(adminTenantStorageKey, tenantId);
  } catch {
    // Ignore storage failures; the current tab will still update React state.
  }
}

export async function fetchHealth(): Promise<HealthResponse> {
  const response = await fetch(`${apiBaseUrl}/api/health`, { credentials: 'include' });

  if (!response.ok) {
    throw new Error(`Health check failed: ${response.status}`);
  }

  return response.json() as Promise<HealthResponse>;
}

async function toApiRequestError(response: Response) {
  let payload: ApiErrorPayload | undefined;
  try {
    payload = (await response.json()) as ApiErrorPayload;
  } catch {
    payload = undefined;
  }
  const message = payload?.message ?? payload?.detail ?? payload?.error ?? `Request failed: ${response.status}`;
  return new ApiRequestError(message, response.status, payload?.code);
}