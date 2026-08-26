import { AuthResponse } from './types';

/**
 * Where the token pair lives, and the only place that knows it.
 *
 * localStorage rather than memory so a reload does not sign the customer out mid-booking, and
 * rather than a cookie because the backend is stateless bearer-token auth with CSRF disabled
 * (`SecurityConfig`) — a cookie the browser attaches automatically is exactly what that setup
 * is not expecting.
 *
 * The refresh token is opaque and rotates on every use (`AuthService.refresh`), so a stored pair
 * is worth one refresh at most before the server invalidates it.
 */

const ACCESS_KEY = 'lumen.accessToken';
const REFRESH_KEY = 'lumen.refreshToken';

/**
 * Broadcast when the session ends without the user asking — a refresh token that the server
 * rejected. AuthContext listens and drops the user; the route guards do the rest. An event
 * rather than a callback because the axios interceptor is module scope and React state is not.
 */
export const SESSION_EXPIRED_EVENT = 'lumen:session-expired';

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

export function getAccessToken(): string | null {
  return safeRead(ACCESS_KEY);
}

export function getRefreshToken(): string | null {
  return safeRead(REFRESH_KEY);
}

export function storeTokens(auth: AuthResponse | TokenPair): void {
  safeWrite(ACCESS_KEY, auth.accessToken);
  safeWrite(REFRESH_KEY, auth.refreshToken);
}

export function clearTokens(): void {
  safeRemove(ACCESS_KEY);
  safeRemove(REFRESH_KEY);
}

/** Clears the pair and tells the app the session is over. */
export function endSession(): void {
  clearTokens();
  window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
}

// Safari's private mode throws on both read and write, and a storage failure is never a reason
// to take the page down — the worst case is a session that does not survive a reload.
function safeRead(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function safeWrite(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    /* ignore */
  }
}

function safeRemove(key: string): void {
  try {
    localStorage.removeItem(key);
  } catch {
    /* ignore */
  }
}
