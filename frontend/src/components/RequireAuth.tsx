import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

/**
 * Route guard for the screens that need an account, and for the admin section Part 9 builds.
 *
 * The redirect carries the attempted location in router state so `LoginPage` can send the
 * customer back to it — a session that lapsed mid-checkout should resume at checkout, not at
 * the listing.
 *
 * `isLoading` matters: on a cold load with a stored token the user is briefly null while
 * `/auth/me` is in flight, and rendering the redirect during that window would sign a
 * signed-in customer out on every refresh.
 */
export default function RequireAuth({ role }: { role?: 'ADMIN' }) {
  const { isAuthenticated, isAdmin, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return <p className="py-16 text-center text-sm text-muted">Loading…</p>;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  // A customer who reaches an admin URL is authenticated, just not entitled — sending them to
  // the login form would be a loop, so they go back to the catalogue.
  if (role === 'ADMIN' && !isAdmin) {
    return <Navigate to="/showings" replace />;
  }

  return <Outlet />;
}
