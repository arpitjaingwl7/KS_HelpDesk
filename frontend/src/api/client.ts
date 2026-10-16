import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { tokenStorage } from '../auth/tokenStorage';

export const API_BASE_URL = '/api/v1';

/**
 * Serialises query params the way Spring expects: arrays repeat the key
 * (`status=OPEN&status=IN_PROGRESS`), and empty / undefined values are dropped.
 */
export function serializeParams(params: Record<string, unknown>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue;
    if (Array.isArray(value)) {
      for (const item of value) {
        if (item !== undefined && item !== null && item !== '') search.append(key, String(item));
      }
    } else {
      search.append(key, String(value));
    }
  }
  return search.toString();
}

export const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { Accept: 'application/json, application/problem+json' },
  paramsSerializer: { serialize: serializeParams },
  timeout: 30_000,
});

type UnauthorizedHandler = () => void;
let unauthorizedHandler: UnauthorizedHandler | null = null;

/** Registered by AuthProvider: clears auth, redirects to /login?next=… and shows a toast. */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler;
}

/** Endpoints where a 401 means "bad credentials", not "session expired". */
const PUBLIC_AUTH_PATHS = ['/auth/login', '/auth/register-organization'];

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = tokenStorage.getToken();
  if (token) config.headers.set('Authorization', `Bearer ${token}`);
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const status = error.response?.status;
    const url = error.config?.url ?? '';
    const isPublicAuthCall = PUBLIC_AUTH_PATHS.some((p) => url.endsWith(p));
    if (status === 401 && !isPublicAuthCall && tokenStorage.getToken()) {
      unauthorizedHandler?.();
    }
    return Promise.reject(error);
  },
);
