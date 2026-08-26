import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, get } from '../api/client';
import { AuthResponse, UserResponse } from '../api/types';
import {
  SESSION_EXPIRED_EVENT,
  clearTokens,
  getRefreshToken,
  getAccessToken,
  storeTokens
} from '../api/tokens';

interface Credentials {
  email: string;
  password: string;
}

interface Registration extends Credentials {
  fullName: string;
}

interface AuthContextValue {
  user: UserResponse | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  /** True until the stored token has been checked against the server on first load. */
  isLoading: boolean;
  login: (credentials: Credentials) => Promise<UserResponse>;
  register: (registration: Registration) => Promise<UserResponse>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

/**
 * Who is signed in, and the three calls that change the answer.
 *
 * Tokens live in `api/tokens.ts` and the axios interceptor refreshes them on its own; this
 * context is the React-visible half — the user object the chrome renders and the guards read.
 *
 * Must sit inside the QueryClientProvider: signing in or out clears the query cache, because
 * `heldByYou` on a seat map and everything under `/bookings/me` mean something different once
 * the caller changes.
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [isLoading, setIsLoading] = useState(() => getAccessToken() !== null);
  const queryClient = useQueryClient();

  /**
   * A stored token proves nothing — it may have expired while the tab was closed, or been
   * revoked by a logout elsewhere. `/auth/me` is the cheapest way to ask, and a 401 on it goes
   * through the same silent refresh as any other call, so a lapsed access token with a live
   * refresh token restores the session instead of ending it.
   */
  useEffect(() => {
    if (!getAccessToken()) return;

    let cancelled = false;
    get<UserResponse>('/auth/me')
      .then(me => {
        if (!cancelled) setUser(me);
      })
      .catch(() => {
        if (!cancelled) {
          clearTokens();
          setUser(null);
        }
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  // The interceptor fires this when a refresh token is rejected. Tokens are already gone by
  // then; all that is left is to drop the user and any data fetched as them.
  useEffect(() => {
    function onSessionExpired() {
      setUser(null);
      queryClient.clear();
    }
    window.addEventListener(SESSION_EXPIRED_EVENT, onSessionExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onSessionExpired);
  }, [queryClient]);

  const adopt = useCallback(
    (auth: AuthResponse) => {
      storeTokens(auth);
      setUser(auth.user);
      // Anything cached as the previous caller (or as nobody) is not this caller's.
      queryClient.clear();
      return auth.user;
    },
    [queryClient]
  );

  const login = useCallback(
    async ({ email, password }: Credentials) => {
      const { data } = await api.post<AuthResponse>('/auth/login', { email, password });
      return adopt(data);
    },
    [adopt]
  );

  const register = useCallback(
    async ({ email, password, fullName }: Registration) => {
      // Registration returns a token pair too, so a new customer lands signed in rather than
      // being bounced to the login form to type the same password again.
      const { data } = await api.post<AuthResponse>('/auth/register', { email, password, fullName });
      return adopt(data);
    },
    [adopt]
  );

  const logout = useCallback(async () => {
    const refreshToken = getRefreshToken();
    try {
      // Revokes the refresh token server-side. Takes the refresh token rather than the access
      // token, so this works even if the access token already expired.
      if (refreshToken) await api.post('/auth/logout', { refreshToken });
    } catch {
      // A logout that the server never heard is still a logout here: the local tokens go
      // regardless, and the refresh token expires on its own.
    } finally {
      clearTokens();
      setUser(null);
      queryClient.clear();
    }
  }, [queryClient]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: user !== null,
      isAdmin: user?.role === 'ADMIN',
      isLoading,
      login,
      register,
      logout
    }),
    [user, isLoading, login, register, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
