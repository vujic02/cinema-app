import { NavLink } from 'react-router-dom';
import { useTheme } from '../context/ThemeContext';

/**
 * Destinations only. The handoff listed all six routes as flat tabs, which put /seats,
 * /checkout and /confirmation in the chrome as if they were places you could go — they are
 * steps in a flow, reached from the screen before them and guarded by RequireBookingState.
 */
const LINKS = [
  { to: '/showings', label: 'Showings' },
  { to: '/bookings', label: 'My Bookings' },
  { to: '/login', label: 'Log In' }
];

export default function NavBar() {
  const { theme, toggleTheme } = useTheme();
  const isDark = theme === 'dark';

  return (
    <header className="sticky top-0 z-20 flex flex-wrap items-center gap-6 border-b border-line bg-surface px-6 py-4">
      <div className="font-bold tracking-widest text-accent-text">LUMEN</div>
      <nav className="flex flex-1 gap-1.5 overflow-x-auto">
        {LINKS.map(link => (
          <NavLink
            key={link.to}
            to={link.to}
            className={({ isActive }) =>
              `whitespace-nowrap rounded-lg px-4 py-2 text-sm font-semibold ${
                isActive ? 'bg-accent text-accent-ink' : 'text-muted hover:text-ink'
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
        aria-pressed={isDark}
        className="relative h-7 w-13 flex-shrink-0 rounded-full border border-line-strong bg-sunken"
      >
        <span
          className={`absolute top-0.5 h-5 w-5 rounded-full bg-accent transition-all ${
            isDark ? 'left-[27px]' : 'left-[3px]'
          }`}
        />
      </button>
    </header>
  );
}
