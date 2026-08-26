import React, { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { InlineError } from '../components/QueryState';
import { useAuth } from '../context/AuthContext';

/** Matches the backend's `@Size(min = 8, max = 72)` — 72 because BCrypt truncates past that. */
const MIN_PASSWORD = 8;
const MAX_PASSWORD = 72;

interface LocationState {
  from?: { pathname: string };
}

export default function LoginPage() {
  const [mode, setMode] = useState<'login' | 'signup'>('login');
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const { login, register, isAuthenticated, isLoading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const isSignup = mode === 'signup';

  /**
   * Where to land after signing in. `RequireAuth` and the seat map both redirect here with the
   * attempted location in router state, so a session that lapsed mid-flow resumes where it
   * stopped instead of dumping the customer back on the listing.
   */
  const destination = (location.state as LocationState | null)?.from?.pathname ?? '/showings';

  // Already signed in — either a bookmark on /login or a second tab that logged in first.
  if (!isLoading && isAuthenticated) {
    return <Navigate to={destination} replace />;
  }

  function switchMode(next: 'login' | 'signup') {
    setMode(next);
    // Carrying a failed sign-in's error onto the sign-up form would explain the wrong thing.
    setError(null);
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);

    // Checked here as well as server-side: the backend has no `confirmPassword` field to
    // compare against, so a typo would otherwise create an account with a password the
    // customer never meant to type.
    if (isSignup && password !== confirmPassword) {
      setError('The two passwords do not match.');
      return;
    }
    if (isSignup && (password.length < MIN_PASSWORD || password.length > MAX_PASSWORD)) {
      setError(`Password must be between ${MIN_PASSWORD} and ${MAX_PASSWORD} characters.`);
      return;
    }

    setBusy(true);
    try {
      if (isSignup) await register({ email, password, fullName });
      else await login({ email, password });
      navigate(destination, { replace: true });
    } catch (caught) {
      // INVALID_CREDENTIALS and EMAIL_TAKEN both arrive with a usable message; the fallback is
      // for a network failure, where there is no server message at all.
      setError(errorMessage(caught, isSignup ? 'Could not create the account.' : 'Could not sign in.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto mt-10 max-w-md rounded-2xl border border-line bg-surface p-8">
      <div className="mb-7 flex gap-1 rounded-lg bg-sunken p-1">
        <button
          onClick={() => switchMode('login')}
          className={`flex-1 rounded-md py-2 text-sm font-bold ${!isSignup ? 'bg-elevated' : 'text-muted'}`}
        >
          Log In
        </button>
        <button
          onClick={() => switchMode('signup')}
          className={`flex-1 rounded-md py-2 text-sm font-bold ${isSignup ? 'bg-elevated' : 'text-muted'}`}
        >
          Sign Up
        </button>
      </div>

      <h1 className="mb-1 text-2xl font-semibold">{isSignup ? 'Create your account' : 'Welcome back'}</h1>
      <p className="mb-6 text-sm text-muted">
        {isSignup ? 'Sign up to start booking tickets' : 'Log in to book your next showing'}
      </p>

      <form onSubmit={submit} className="flex flex-col gap-4">
        {isSignup && (
          <label className="block">
            <span className="mb-1.5 block text-xs font-semibold text-muted">Full name</span>
            <input
              type="text"
              required
              maxLength={120}
              autoComplete="name"
              value={fullName}
              onChange={e => setFullName(e.target.value)}
              placeholder="Jamie Rivera"
              className="w-full rounded-lg border border-line-strong bg-raised px-3 py-2.5 text-sm outline-none focus:border-accent"
            />
          </label>
        )}
        <label className="block">
          <span className="mb-1.5 block text-xs font-semibold text-muted">Email</span>
          <input
            type="email"
            required
            maxLength={255}
            autoComplete="email"
            value={email}
            onChange={e => setEmail(e.target.value)}
            placeholder="you@example.com"
            className="w-full rounded-lg border border-line-strong bg-raised px-3 py-2.5 text-sm outline-none focus:border-accent"
          />
        </label>
        <label className="block">
          <span className="mb-1.5 block text-xs font-semibold text-muted">Password</span>
          <input
            type="password"
            required
            autoComplete={isSignup ? 'new-password' : 'current-password'}
            value={password}
            onChange={e => setPassword(e.target.value)}
            placeholder="••••••••"
            className="w-full rounded-lg border border-line-strong bg-raised px-3 py-2.5 text-sm outline-none focus:border-accent"
          />
        </label>
        {isSignup && (
          <label className="block">
            <span className="mb-1.5 block text-xs font-semibold text-muted">Confirm password</span>
            <input
              type="password"
              required
              autoComplete="new-password"
              value={confirmPassword}
              onChange={e => setConfirmPassword(e.target.value)}
              placeholder="••••••••"
              className="w-full rounded-lg border border-line-strong bg-raised px-3 py-2.5 text-sm outline-none focus:border-accent"
            />
          </label>
        )}

        {error && <InlineError message={error} />}

        <button
          type="submit"
          disabled={busy}
          className="mt-2 rounded-lg bg-accent py-3 text-sm font-bold text-accent-ink disabled:cursor-not-allowed disabled:bg-sunken disabled:text-disabled"
        >
          {busy ? 'Please wait…' : isSignup ? 'Create Account' : 'Log In'}
        </button>
      </form>

      <p className="mt-4 text-center text-sm text-muted">
        {isSignup ? 'Already have an account?' : "Don't have an account?"}{' '}
        <button onClick={() => switchMode(isSignup ? 'login' : 'signup')} className="text-accent-text">
          {isSignup ? 'Log in' : 'Sign up'}
        </button>
      </p>
    </div>
  );
}
