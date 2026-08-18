import React, { createContext, useContext, useState } from 'react';
import { Movie, Showtime, Booking } from '../types';
import { INITIAL_BOOKINGS, SEAT_PRICE, VENUE_LAYOUT } from '../data/mockData';

interface ConfirmationData {
  movieTitle: string;
  hue: number;
  venue: string;
  date: string;
  time: string;
  seats: string[];
  total: number;
  ref: string;
}

interface BookingContextValue {
  selectedMovie: Movie | null;
  selectedShowtime: Showtime | null;
  selectedSeats: string[];
  bookings: Booking[];
  confirmation: ConfirmationData | null;
  selectShowtime: (movie: Movie, showtime: Showtime) => void;
  toggleSeat: (seatId: string) => void;
  confirmPurchase: () => void;
  viewBooking: (booking: Booking) => void;
}

const BookingContext = createContext<BookingContextValue | undefined>(undefined);

export function BookingProvider({ children }: { children: React.ReactNode }) {
  const [selectedMovie, setSelectedMovie] = useState<Movie | null>(null);
  const [selectedShowtime, setSelectedShowtime] = useState<Showtime | null>(null);
  const [selectedSeats, setSelectedSeats] = useState<string[]>([]);
  const [bookings, setBookings] = useState<Booking[]>(INITIAL_BOOKINGS);
  const [confirmation, setConfirmation] = useState<ConfirmationData | null>(null);

  // TODO (Part 8): subscribe to STOMP/SockJS at /ws, topic /topic/showings/{showingId}, and
  // merge the { showingId, seatId, status } frames into a shared seatStatus map so other
  // customers' holds show live. `selectedSeats` stays the optimistic local view on top of it.
  //
  // TODO (Part 7): toggleSeat becomes POST/DELETE /api/showings/{id}/seats/{seatId}/hold —
  // selecting a seat is a server-side hold with a TTL, not just local state, and a 409
  // SEAT_HELD has to revert the selection.

  function selectShowtime(movie: Movie, showtime: Showtime) {
    setSelectedMovie(movie);
    setSelectedShowtime(showtime);
    setSelectedSeats([]);
  }

  function toggleSeat(seatId: string) {
    if (VENUE_LAYOUT.reservedSeatIds.includes(seatId)) return;
    setSelectedSeats(seats => (seats.includes(seatId) ? seats.filter(s => s !== seatId) : [...seats, seatId]));
  }

  function confirmPurchase() {
    if (!selectedMovie || !selectedShowtime) return;
    const total = selectedSeats.length * SEAT_PRICE;
    const ref = 'LUM-' + Math.floor(10000 + Math.random() * 89999);
    const data: ConfirmationData = {
      movieTitle: selectedMovie.title, hue: selectedMovie.hue, venue: selectedShowtime.venue,
      date: selectedShowtime.date, time: selectedShowtime.time, seats: [...selectedSeats], total, ref
    };
    setConfirmation(data);
    setBookings(b => [{ id: 'b' + Date.now(), status: 'upcoming', ...data }, ...b]);
  }

  function viewBooking(booking: Booking) {
    setConfirmation({ ...booking });
  }

  return (
    <BookingContext.Provider value={{ selectedMovie, selectedShowtime, selectedSeats, bookings, confirmation, selectShowtime, toggleSeat, confirmPurchase, viewBooking }}>
      {children}
    </BookingContext.Provider>
  );
}

export function useBooking() {
  const ctx = useContext(BookingContext);
  if (!ctx) throw new Error('useBooking must be used within BookingProvider');
  return ctx;
}
