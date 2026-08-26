import { Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from './context/AuthContext';
import { ThemeProvider } from './context/ThemeContext';
import { BookingProvider } from './context/BookingContext';
import NavBar from './components/NavBar';
import RequireAuth from './components/RequireAuth';
import RequireBookingState from './components/RequireBookingState';
import LoginPage from './pages/LoginPage';
import ShowingsPage from './pages/ShowingsPage';
import SeatSelectionPage from './pages/SeatSelectionPage';
import CheckoutPage from './pages/CheckoutPage';
import ConfirmationPage from './pages/ConfirmationPage';
import MyBookingsPage from './pages/MyBookingsPage';

/**
 * Created once at module scope rather than inside the component, so a re-render cannot throw
 * away every cached query.
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // A 401 is the interceptor's job — it refreshes and replays. A 404 or a 409 is an answer,
      // not a hiccup. Retrying either just delays the message the customer needs to read.
      retry: (failureCount, error) => {
        const status = (error as { response?: { status?: number } }).response?.status;
        if (status && status >= 400 && status < 500) return false;
        return failureCount < 2;
      },
      staleTime: 30_000,
      refetchOnWindowFocus: false
    }
  }
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        {/* AuthProvider sits inside QueryClientProvider: signing in or out clears the cache. */}
        <AuthProvider>
          <BookingProvider>
            <div className="min-h-screen bg-page text-ink">
              <NavBar />
              <main className="mx-auto max-w-[1180px] px-6 py-8">
                <Routes>
                  {/* Browsing needs no account — the catalogue endpoints are public, so the
                      landing page is the listing rather than the login form. */}
                  <Route path="/" element={<Navigate to="/showings" replace />} />
                  <Route path="/login" element={<LoginPage />} />
                  <Route path="/showings" element={<ShowingsPage />} />

                  {/* The booking flow. Each step is reachable only once the one before it has
                      produced what it needs; the steps that call an authenticated endpoint also
                      need a token. The seat map is deliberately not among them — it reads
                      anonymously, and the sign-in prompt comes when a seat is clicked. */}
                  <Route element={<RequireBookingState stage="seats" />}>
                    <Route path="/seats" element={<SeatSelectionPage />} />
                  </Route>
                  <Route element={<RequireAuth />}>
                    <Route path="/bookings" element={<MyBookingsPage />} />
                    <Route element={<RequireBookingState stage="checkout" />}>
                      <Route path="/checkout" element={<CheckoutPage />} />
                    </Route>
                    <Route element={<RequireBookingState stage="confirmation" />}>
                      <Route path="/confirmation" element={<ConfirmationPage />} />
                    </Route>
                  </Route>

                  <Route path="*" element={<Navigate to="/showings" replace />} />
                </Routes>
              </main>
            </div>
          </BookingProvider>
        </AuthProvider>
      </ThemeProvider>
    </QueryClientProvider>
  );
}
