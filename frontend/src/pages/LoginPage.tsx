import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';

export default function LoginPage() {
  const [mode, setMode] = useState<'login' | 'signup'>('login');
  const navigate = useNavigate();
  const isSignup = mode === 'signup';

  function submit(e: React.FormEvent) {
    e.preventDefault(); // cosmetic only — no real validation
    navigate('/showings');
  }

  return (
    <div className="mx-auto mt-10 max-w-md rounded-2xl border border-zinc-200 bg-zinc-50 p-8 dark:border-zinc-800 dark:bg-zinc-900">
      <div className="mb-7 flex gap-1 rounded-lg bg-zinc-200 p-1 dark:bg-zinc-800">
        <button onClick={() => setMode('login')} className={`flex-1 rounded-md py-2 text-sm font-bold ${!isSignup ? 'bg-white text-zinc-900 dark:bg-zinc-700 dark:text-zinc-50' : 'text-zinc-500'}`}>Log In</button>
        <button onClick={() => setMode('signup')} className={`flex-1 rounded-md py-2 text-sm font-bold ${isSignup ? 'bg-white text-zinc-900 dark:bg-zinc-700 dark:text-zinc-50' : 'text-zinc-500'}`}>Sign Up</button>
      </div>

      <h1 className="mb-1 text-2xl font-semibold">{isSignup ? 'Create your account' : 'Welcome back'}</h1>
      <p className="mb-6 text-sm text-zinc-500 dark:text-zinc-400">{isSignup ? 'Sign up to start booking tickets' : 'Log in to book your next showing'}</p>

      <form onSubmit={submit} className="flex flex-col gap-4">
        {isSignup && (
          <label className="block">
            <span className="mb-1.5 block text-xs font-semibold text-zinc-500 dark:text-zinc-400">Full name</span>
            <input type="text" placeholder="Jamie Rivera" className="w-full rounded-lg border border-zinc-300 bg-white px-3 py-2.5 text-sm outline-none dark:border-zinc-700 dark:bg-zinc-800" />
          </label>
        )}
        <label className="block">
          <span className="mb-1.5 block text-xs font-semibold text-zinc-500 dark:text-zinc-400">Email</span>
          <input type="email" placeholder="you@example.com" className="w-full rounded-lg border border-zinc-300 bg-white px-3 py-2.5 text-sm outline-none dark:border-zinc-700 dark:bg-zinc-800" />
        </label>
        <label className="block">
          <span className="mb-1.5 block text-xs font-semibold text-zinc-500 dark:text-zinc-400">Password</span>
          <input type="password" placeholder="••••••••" className="w-full rounded-lg border border-zinc-300 bg-white px-3 py-2.5 text-sm outline-none dark:border-zinc-700 dark:bg-zinc-800" />
        </label>
        {isSignup && (
          <label className="block">
            <span className="mb-1.5 block text-xs font-semibold text-zinc-500 dark:text-zinc-400">Confirm password</span>
            <input type="password" placeholder="••••••••" className="w-full rounded-lg border border-zinc-300 bg-white px-3 py-2.5 text-sm outline-none dark:border-zinc-700 dark:bg-zinc-800" />
          </label>
        )}
        <button type="submit" className="mt-2 rounded-lg bg-teal-500 py-3 text-sm font-bold text-zinc-950">
          {isSignup ? 'Create Account' : 'Log In'}
        </button>
      </form>

      <p className="mt-4 text-center text-sm text-zinc-500 dark:text-zinc-400">
        {isSignup ? 'Already have an account?' : "Don't have an account?"}{' '}
        <button onClick={() => setMode(isSignup ? 'login' : 'signup')} className="text-teal-600 dark:text-teal-400">
          {isSignup ? 'Log in' : 'Sign up'}
        </button>
      </p>
    </div>
  );
}
