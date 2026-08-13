import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { ThemeProvider } from './context/ThemeContext';
import { BookingProvider } from './context/BookingContext';
import NavBar from './components/NavBar';
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
        <div className="min-h-screen bg-white text-zinc-900 dark:bg-zinc-950 dark:text-zinc-50">
          <NavBar />
          <main className="mx-auto max-w-[1180px] px-6 py-8">
            <Routes>
              <Route path="/" element={<Navigate to="/login" replace />} />
              <Route path="/login" element={<LoginPage />} />
              <Route path="/showings" element={<ShowingsPage />} />
              <Route path="/seats" element={<SeatSelectionPage />} />
              <Route path="/checkout" element={<CheckoutPage />} />
              <Route path="/confirmation" element={<ConfirmationPage />} />
              <Route path="/bookings" element={<MyBookingsPage />} />
            </Routes>
          </main>
        </div>
      </BookingProvider>
    </ThemeProvider>
  );
}
