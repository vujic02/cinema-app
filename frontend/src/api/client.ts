import axios, { AxiosError, AxiosRequestConfig, InternalAxiosRequestConfig } from 'axios';
import { ApiError, AuthResponse } from './types';
import { endSession, getAccessToken, getRefreshToken, storeTokens } from './tokens';

/**
 * The one axios instance the app talks through.
 *
 * `baseURL: '/api'` and nothing else: the dev server proxies `/api` to :8080 and Nginx does the
 * same in production (TECH.md §4), so the browser only ever sees one origin and the backend
 * needs no CORS config. There is deliberately no `VITE_API_URL` escape hatch — introducing one
 * would mean introducing CORS.
 */
export const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' }
});

/** Endpoints that must never be retried through the refresh path — refresh included. */
const AUTH_ROUTES = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout'];

interface RetriableConfig extends InternalAxiosRequestConfig {
  /** Set once a request has already been replayed, so a second 401 gives up instead of looping. */
  _retried?: boolean;
}

api.interceptors.request.use(config => {
  const token = getAccessToken();
  if (token && !isAuthRoute(config.url)) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/**
 * Silent refresh.
 *
 * A 401 on any authenticated call means the 15-minute access token lapsed. The refresh token is
 * spent for a new pair and the original request is replayed, so the customer never sees a login
 * screen mid-booking.
 *
 * The backend answers 401 for "not authenticated" and 403 for "authenticated but not allowed"
 * (`RestAuthErrorHandler`), so this fires on exactly the recoverable case — an admin route hit
 * by a customer stays a 403 and is not retried.
 */
api.interceptors.response.use(
  response => response,
  async (error: AxiosError<ApiError>) => {
    const config = error.config as RetriableConfig | undefined;

    if (
      error.response?.status !== 401 ||
      !config ||
      config._retried ||
      isAuthRoute(config.url) ||
      !getRefreshToken()
    ) {
      return Promise.reject(error);
    }

    try {
      await refreshOnce();
    } catch {
      // The refresh token is gone or already spent. endSession() has fired; surface the
      // original 401 so the caller sees the request that failed, not the refresh that did.
      return Promise.reject(error);
    }

    config._retried = true;
    // The request interceptor re-reads the token store, so the replay picks up the new one.
    return api.request(config);
  }
);

/**
 * At most one refresh in flight.
 *
 * Refresh tokens rotate — `/auth/refresh` revokes the token it was handed — so two parallel
 * 401s each refreshing would leave the second holding a token the server has already killed.
 * Every caller waits on the same promise instead, and the winner writes the new pair.
 */
let refreshInFlight: Promise<void> | null = null;

function refreshOnce(): Promise<void> {
  if (!refreshInFlight) {
    refreshInFlight = performRefresh().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

async function performRefresh(): Promise<void> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) throw new Error('No refresh token');

  try {
    // Bare axios, not `api`: going through the instance would put this call under the same
    // interceptor and a failing refresh would try to refresh itself.
    const { data } = await axios.post<AuthResponse>('/api/auth/refresh', { refreshToken });
    storeTokens(data);
  } catch (error) {
    endSession();
    throw error;
  }
}

function isAuthRoute(url?: string): boolean {
  return !!url && AUTH_ROUTES.some(route => url.startsWith(route));
}

/**
 * The backend's error code for a failed request, e.g. `SEAT_HELD`, `HOLD_EXPIRED`,
 * `INVALID_CREDENTIALS`. Screens branch on this rather than on the message text.
 */
export function errorCode(error: unknown): string | undefined {
  return axios.isAxiosError<ApiError>(error) ? error.response?.data?.code : undefined;
}

/**
 * Something safe to show a customer. Prefers the server's message, falls back to the first
 * field error on a validation failure, then to `fallback` — never a raw axios string like
 * "Request failed with status code 409".
 */
export function errorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (!axios.isAxiosError<ApiError>(error)) return fallback;

  const data = error.response?.data;
  if (data?.code === 'VALIDATION_FAILED' && data.fieldErrors) {
    const first = Object.values(data.fieldErrors)[0];
    if (first) return first;
  }
  if (data?.message) return data.message;
  if (!error.response) return 'Cannot reach the server. Check your connection and try again.';
  return fallback;
}

/** Convenience for the query functions below — unwraps `data` so hooks stay one-liners. */
export async function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const { data } = await api.get<T>(url, config);
  return data;
}
