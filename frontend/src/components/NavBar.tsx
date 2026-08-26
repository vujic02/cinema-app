import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';

/**
 * Destinations only. The handoff listed all six routes as flat tabs, which put /seats,
 * /checkout and /confirmation in the chrome as if they were places you could go — they are
 * steps in a flow, reached from the screen before them and guarded by RequireBookingState.
 */
const PUBLIC_LINKS = [{ to: '/showings', label: 'Showings' }];
const CUSTOMER_LINKS = [{ to: '/bookings', label: 'My Bookings' }];

export default function NavBar() {
  const { theme, toggleTheme } = useTheme();
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();
  const isDark = theme === 'dark';

  // My Bookings is hidden rather than shown-and-bounced: the route requires a token, so an
  // anonymous visitor clicking it would only ever land on the login form.
  const links = isAuthenticated ? [...PUBLIC_LINKS, ...CUSTOMER_LINKS] : PUBLIC_LINKS;

  async function signOut() {
    await logout();
    navigate('/showings');
  }

  return (
    <header className="sticky top-0 z-20 flex flex-wrap items-center gap-6 border-b border-line bg-surface px-6 py-4">
      <div className="font-bold tracking-widest text-accent-text">LUMEN</div>
      <nav className="flex flex-1 gap-1.5 overflow-x-auto">
        {links.map(link => (
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
        {isAuthenticated ? (
          <button
            onClick={signOut}
            className="whitespace-nowrap rounded-lg px-4 py-2 text-sm font-semibold text-muted hover:text-ink"
          >
            Log Out
          </button>
        ) : (
          <NavLink
            to="/login"
            className={({ isActive }) =>
              `whitespace-nowrap rounded-lg px-4 py-2 text-sm font-semibold ${
                isActive ? 'bg-accent text-accent-ink' : 'text-muted hover:text-ink'
              }`
            }
          >
            Log In
          </NavLink>
        )}
      </nav>
      {user && <span className="hidden text-sm text-muted sm:inline">{user.fullName}</span>}
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
