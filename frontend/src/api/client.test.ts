import axios, { AxiosError, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { api, errorCode, errorMessage } from './client';
import { SESSION_EXPIRED_EVENT, clearTokens, getAccessToken, getRefreshToken, storeTokens } from './tokens';

/**
 * The silent-refresh interceptor, tested at the adapter seam.
 *
 * Both axios entry points the client uses are stubbed: `api.defaults.adapter` for ordinary
 * calls and `axios.defaults.adapter` for the bare `axios.post('/api/auth/refresh')` the refresh
 * deliberately makes outside the instance. Stubbing at that level exercises the real
 * interceptors, the real single-flight promise and the real token store — everything except the
 * socket.
 *
 * The cases that matter are the ones that are invisible until they bite: two parallel 401s must
 * spend the refresh token once, because `/auth/refresh` revokes the token it is handed.
 */

interface Recorded {
  url: string;
  method: string;
  authorization?: string;
}

let calls: Recorded[] = [];
let handler: (config: InternalAxiosRequestConfig) => Promise<AxiosResponse>;

function fullUrl(config: InternalAxiosRequestConfig): string {
  return `${config.baseURL ?? ''}${config.url ?? ''}`;
}

function ok<T>(config: InternalAxiosRequestConfig, data: T, status = 200): Promise<AxiosResponse<T>> {
  return Promise.resolve({ data, status, statusText: 'OK', headers: {}, config });
}

/** Rejects the way a real adapter does: an AxiosError carrying the server's ApiError body. */
function fail(config: InternalAxiosRequestConfig, status: number, code = 'UNAUTHORIZED', message = 'nope') {
  const response: AxiosResponse = {
    data: { code, message, path: config.url ?? '', timestamp: '2026-08-26T10:00:00Z' },
    status,
    statusText: '',
    headers: {},
    config
  };
  return Promise.reject(new AxiosError(`Request failed with status code ${status}`, String(status), config, {}, response));
}

const adapter = (config: InternalAxiosRequestConfig) => {
  calls.push({
    url: fullUrl(config),
    method: (config.method ?? 'get').toLowerCase(),
    authorization: config.headers?.Authorization as string | undefined
  });
  return handler(config);
};

const originalInstanceAdapter = api.defaults.adapter;
const originalGlobalAdapter = axios.defaults.adapter;

beforeEach(() => {
  calls = [];
  clearTokens();
  // The instance copies axios.defaults at create time, so the global assignment alone would not
  // reach `api` — both have to be replaced.
  api.defaults.adapter = adapter;
  axios.defaults.adapter = adapter;
  handler = config => ok(config, { fine: true });
});

afterEach(() => {
  api.defaults.adapter = originalInstanceAdapter;
  axios.defaults.adapter = originalGlobalAdapter;
});

function storePair(access = 'access-1', refresh = 'refresh-1') {
  storeTokens({ accessToken: access, refreshToken: refresh });
}

describe('request interceptor', () => {
  it('attaches the stored access token', async () => {
    storePair();

    await api.get('/bookings/me');

    expect(calls[0].authorization).toBe('Bearer access-1');
  });

  it('sends nothing when there is no token', async () => {
    await api.get('/movies');

    expect(calls[0].authorization).toBeUndefined();
  });

  /**
   * Logging in while a stale token is still in storage must not send it: the login endpoint is
   * permitAll, but a token the server rejects would produce a 401 the interceptor would then
   * try to recover from — during the very call that is meant to establish the session.
   */
  it('sends nothing on the auth routes even when a token is stored', async () => {
    storePair();

    await api.post('/auth/login', { email: 'a@b.c', password: 'password' });

    expect(calls[0].authorization).toBeUndefined();
  });
});

describe('silent refresh', () => {
  it('refreshes on a 401 and replays the original request with the new token', async () => {
    storePair();
    let firstAttempt = true;

    handler = config => {
      const url = fullUrl(config);
      if (url === '/api/auth/refresh') {
        return ok(config, {
          accessToken: 'access-2',
          refreshToken: 'refresh-2',
          tokenType: 'Bearer',
          expiresIn: 900,
          user: { id: 1, email: 'a@b.c', fullName: 'A', role: 'CUSTOMER' }
        });
      }
      if (firstAttempt) {
        firstAttempt = false;
        return fail(config, 401);
      }
      return ok(config, { seats: 3 });
    };

    const { data } = await api.get('/bookings/me');

    expect(data).toEqual({ seats: 3 });
    expect(calls.map(call => call.url)).toEqual([
      '/api/bookings/me',
      '/api/auth/refresh',
      '/api/bookings/me'
    ]);
    // The replay picks the new token up because the request interceptor re-reads the store.
    expect(calls[2].authorization).toBe('Bearer access-2');
    expect(getAccessToken()).toBe('access-2');
    expect(getRefreshToken()).toBe('refresh-2');
  });

  /**
   * The reason `refreshInFlight` exists. `/auth/refresh` rotates — it revokes the token it was
   * given — so a second concurrent refresh would present a token the server has already killed
   * and end a session that was perfectly healthy.
   */
  it('spends the refresh token once when several requests 401 together', async () => {
    storePair();
    const expired = new Set(['/api/bookings/me', '/api/movies', '/api/venues']);

    handler = config => {
      const url = fullUrl(config);
      if (url === '/api/auth/refresh') {
        return ok(config, {
          accessToken: 'access-2',
          refreshToken: 'refresh-2',
          tokenType: 'Bearer',
          expiresIn: 900,
          user: { id: 1, email: 'a@b.c', fullName: 'A', role: 'CUSTOMER' }
        });
      }
      if (expired.has(url)) {
        expired.delete(url);
        return fail(config, 401);
      }
      return ok(config, { url });
    };

    const results = await Promise.all([
      api.get('/bookings/me'),
      api.get('/movies'),
      api.get('/venues')
    ]);

    expect(results.map(r => r.data)).toEqual([
      { url: '/api/bookings/me' },
      { url: '/api/movies' },
      { url: '/api/venues' }
    ]);
    expect(calls.filter(call => call.url === '/api/auth/refresh')).toHaveLength(1);
  });

  it('ends the session when the refresh token is rejected', async () => {
    storePair();
    const expired = vi.fn();
    window.addEventListener(SESSION_EXPIRED_EVENT, expired);

    handler = config =>
      fullUrl(config) === '/api/auth/refresh'
        ? fail(config, 401, 'REFRESH_TOKEN_INVALID', 'Refresh token is not valid')
        : fail(config, 401);

    // The caller sees the request that actually failed, not the refresh attempt behind it.
    await expect(api.get('/bookings/me')).rejects.toMatchObject({
      response: { status: 401, data: { path: '/bookings/me' } }
    });

    expect(expired).toHaveBeenCalledTimes(1);
    expect(getAccessToken()).toBeNull();
    expect(getRefreshToken()).toBeNull();

    window.removeEventListener(SESSION_EXPIRED_EVENT, expired);
  });

  it('gives up after one replay rather than looping on a persistent 401', async () => {
    storePair();

    handler = config =>
      fullUrl(config) === '/api/auth/refresh'
        ? ok(config, {
            accessToken: 'access-2',
            refreshToken: 'refresh-2',
            tokenType: 'Bearer',
            expiresIn: 900,
            user: { id: 1, email: 'a@b.c', fullName: 'A', role: 'CUSTOMER' }
          })
        : fail(config, 401);

    await expect(api.get('/bookings/me')).rejects.toBeInstanceOf(AxiosError);

    expect(calls.filter(call => call.url === '/api/auth/refresh')).toHaveLength(1);
    expect(calls.filter(call => call.url === '/api/bookings/me')).toHaveLength(2);
  });

  /**
   * A customer hitting an admin route is authenticated and simply not entitled. The backend
   * answers 403 for exactly this (`RestAuthErrorHandler`), and refreshing a perfectly valid
   * token would not change the answer.
   */
  it('does not refresh on a 403', async () => {
    storePair();
    handler = config => fail(config, 403, 'FORBIDDEN', 'You do not have access to this resource');

    await expect(api.get('/admin/bookings')).rejects.toBeInstanceOf(AxiosError);

    expect(calls.map(call => call.url)).toEqual(['/api/admin/bookings']);
  });

  it('does not refresh when there is no refresh token to spend', async () => {
    handler = config => fail(config, 401);

    await expect(api.get('/bookings/me')).rejects.toBeInstanceOf(AxiosError);

    expect(calls.map(call => call.url)).toEqual(['/api/bookings/me']);
  });

  /** A failed login is a 401 the customer has to read, not one the client can recover from. */
  it('does not refresh a failed login', async () => {
    storePair();
    handler = config => fail(config, 401, 'INVALID_CREDENTIALS', 'Email or password is incorrect');

    await expect(api.post('/auth/login', {})).rejects.toBeInstanceOf(AxiosError);

    expect(calls.map(call => call.url)).toEqual(['/api/auth/login']);
  });
});

describe('errorCode / errorMessage', () => {
  it('reads the backend code the screens branch on', async () => {
    handler = config => fail(config, 409, 'SEAT_HELD', 'Someone else is holding that seat');

    const error = await api.get('/showings/1/seat-map').catch(caught => caught);

    expect(errorCode(error)).toBe('SEAT_HELD');
    expect(errorMessage(error)).toBe('Someone else is holding that seat');
  });

  it('surfaces the first field error on a validation failure', async () => {
    handler = config =>
      Promise.reject(
        new AxiosError('Request failed with status code 400', '400', config, {}, {
          data: {
            code: 'VALIDATION_FAILED',
            message: 'Request validation failed',
            path: '/auth/register',
            fieldErrors: { password: 'Password must be between 8 and 72 characters' },
            timestamp: '2026-08-26T10:00:00Z'
          },
          status: 400,
          statusText: '',
          headers: {},
          config
        })
      );

    const error = await api.post('/auth/register', {}).catch(caught => caught);

    // The generic "Request validation failed" says nothing useful; the field message does.
    expect(errorMessage(error)).toBe('Password must be between 8 and 72 characters');
  });

  it('explains a request that never reached the server', async () => {
    handler = config => Promise.reject(new AxiosError('Network Error', 'ERR_NETWORK', config));

    const error = await api.get('/movies').catch(caught => caught);

    expect(errorCode(error)).toBeUndefined();
    expect(errorMessage(error)).toMatch(/Cannot reach the server/);
  });

  it('falls back for anything that is not an axios error at all', () => {
    expect(errorMessage(new Error('boom'), 'Could not load showtimes.')).toBe('Could not load showtimes.');
  });
});
