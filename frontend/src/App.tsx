import { Routes, Route, Navigate } from 'react-router-dom';
import { ThemeProvider } from './context/ThemeContext';
import { BookingProvider } from './context/BookingContext';
import NavBar from './components/NavBar';
import RequireBookingState from './components/RequireBookingState';
import LoginPage from './pages/LoginPage';
import ShowingsPage from './pages/ShowingsPage';
import SeatSelectionPage from './pages/SeatSelectionPage';
import CheckoutPage from './pages/CheckoutPage';
import ConfirmationPage from './pages/ConfirmationPage';
import MyBookingsPage from './pages/MyBookingsPage';

export default function App() {
  return (
    <ThemeProvider>
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
              <Route path="/bookings" element={<MyBookingsPage />} />

              {/* The booking flow. Each step is reachable only once the one before it has
                  produced what it needs. */}
              <Route element={<RequireBookingState stage="seats" />}>
                <Route path="/seats" element={<SeatSelectionPage />} />
              </Route>
              <Route element={<RequireBookingState stage="checkout" />}>
                <Route path="/checkout" element={<CheckoutPage />} />
              </Route>
              <Route element={<RequireBookingState stage="confirmation" />}>
                <Route path="/confirmation" element={<ConfirmationPage />} />
              </Route>

              <Route path="*" element={<Navigate to="/showings" replace />} />
            </Routes>
          </main>
        </div>
      </BookingProvider>
    </ThemeProvider>
  );
}
