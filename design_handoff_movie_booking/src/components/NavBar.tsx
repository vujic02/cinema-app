import React from 'react';
import { NavLink } from 'react-router-dom';
import { useTheme } from '../context/ThemeContext';

const LINKS = [
  { to: '/login', label: 'Login' },
  { to: '/showings', label: 'Showings' },
  { to: '/seats', label: 'Seats' },
  { to: '/checkout', label: 'Checkout' },
  { to: '/confirmation', label: 'Confirmation' },
  { to: '/bookings', label: 'My Bookings' }
];

export default function NavBar() {
  const { theme, toggleTheme } = useTheme();

  return (
    <header className="sticky top-0 z-20 flex flex-wrap items-center gap-6 border-b border-zinc-200 bg-zinc-50 px-6 py-4 dark:border-zinc-800 dark:bg-zinc-900">
      <div className="font-bold tracking-widest text-teal-600 dark:text-teal-400">LUMEN</div>
      <nav className="flex flex-1 gap-1.5 overflow-x-auto">
        {LINKS.map(link => (
          <NavLink
            key={link.to}
            to={link.to}
            className={({ isActive }) =>
              `whitespace-nowrap rounded-lg px-4 py-2 text-sm font-semibold ${
                isActive ? 'bg-teal-500 text-zinc-950' : 'text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-zinc-50'
              }`
            }
          >
            {link.label}
          </NavLink>
        ))}
      </nav>
      <button
        onClick={toggleTheme}
        aria-label="Toggle theme"
        className="relative h-7 w-13 flex-shrink-0 rounded-full border border-zinc-300 bg-zinc-200 dark:border-zinc-700 dark:bg-zinc-800"
        style={{ width: 52 }}
      >
        <span
          className="absolute top-0.5 h-5 w-5 rounded-full bg-teal-500 transition-all"
          style={{ left: theme === 'dark' ? 27 : 3 }}
        />
      </button>
    </header>
  );
}
