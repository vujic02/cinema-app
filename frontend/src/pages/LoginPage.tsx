import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';

// TODO: Part 7 wires this to POST /api/auth/login|register through AuthContext. Until then the
// form is cosmetic — submitting navigates on, and nothing is validated or sent.

export default function LoginPage() {
  const [mode, setMode] = useState<'login' | 'signup'>('login');
  const navigate = useNavigate();
  const isSignup = mode === 'signup';

  function submit(e: React.FormEvent) {
    e.preventDefault();
    navigate('/showings');
  }

  return (
    <div className="mx-auto mt-10 max-w-md rounded-2xl border border-line bg-surface p-8">
      <div className="mb-7 flex gap-1 rounded-lg bg-sunken p-1">
        <button
          onClick={() => setMode('login')}
          className={`flex-1 rounded-md py-2 text-sm font-bold ${!isSignup ? 'bg-elevated' : 'text-muted'}`}
        >
          Log In
        </button>
        <button
          onClick={() => setMode('signup')}
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
              placeholder="Jamie Rivera"
              className="w-full rounded-lg border border-line-strong bg-raised px-3 py-2.5 text-sm outline-none focus:border-accent"
            />
          </label>
        )}
        <label className="block">
          <span className="mb-1.5 block text-xs font-semibold text-muted">Email</span>
          <input
            type="email"
            placeholder="you@example.com"
            className="w-full rounded-lg border border-line-strong bg-raised px-3 py-2.5 text-sm outline-none focus:border-accent"
          />
        </label>
        <label className="block">
          <span className="mb-1.5 block text-xs font-semibold text-muted">Password</span>
          <input
            type="password"
            placeholder="••••••••"
            className="w-full rounded-lg border border-line-strong bg-raised px-3 py-2.5 text-sm outline-none focus:border-accent"
          />
        </label>
        {isSignup && (
          <label className="block">
            <span className="mb-1.5 block text-xs font-semibold text-muted">Confirm password</span>
            <input
              type="password"
              placeholder="••••••••"
              className="w-full rounded-lg border border-line-strong bg-raised px-3 py-2.5 text-sm outline-none focus:border-accent"
            />
          </label>
        )}
        <button type="submit" className="mt-2 rounded-lg bg-accent py-3 text-sm font-bold text-accent-ink">
          {isSignup ? 'Create Account' : 'Log In'}
        </button>
      </form>

      <p className="mt-4 text-center text-sm text-muted">
        {isSignup ? 'Already have an account?' : "Don't have an account?"}{' '}
        <button onClick={() => setMode(isSignup ? 'login' : 'signup')} className="text-accent-text">
          {isSignup ? 'Log in' : 'Sign up'}
        </button>
      </p>
    </div>
  );
}
