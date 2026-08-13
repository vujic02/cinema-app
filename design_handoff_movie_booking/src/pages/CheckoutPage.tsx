import React from 'react';
import { useNavigate } from 'react-router-dom';
import { SEAT_PRICE, posterGradient } from '../data/mockData';
import { useBooking } from '../context/BookingContext';

export default function CheckoutPage() {
  const { selectedMovie, selectedShowtime, selectedSeats, confirmPurchase } = useBooking();
  const navigate = useNavigate();
  const movie = selectedMovie;
  const showtime = selectedShowtime;
  const seats = selectedSeats.length ? selectedSeats : [];

  function confirm() {
    confirmPurchase();
    navigate('/confirmation');
  }

  return (
    <div className="mx-auto max-w-lg">
      <h1 className="mb-5 text-2xl font-semibold">Order Summary</h1>
      <div className="flex flex-col gap-4 rounded-2xl border border-zinc-200 bg-zinc-50 p-6 dark:border-zinc-800 dark:bg-zinc-900">
        <div className="flex gap-4">
          <div style={posterGradient(movie?.hue ?? 0)} className="flex h-21 w-16 flex-shrink-0 items-center justify-center rounded-lg font-mono text-[8px] text-white/50" style={{ width: 64, height: 84 }}>
            POSTER
          </div>
          <div>
            <h3 className="text-lg font-semibold">{movie?.title}</h3>
            <p className="mt-1.5 text-sm text-zinc-500 dark:text-zinc-400">{showtime ? `${showtime.venue} · ${showtime.date}, ${showtime.time}` : ''}</p>
          </div>
        </div>
        <div className="h-px bg-zinc-200 dark:bg-zinc-800" />
        <div className="flex justify-between text-sm"><span className="text-zinc-500 dark:text-zinc-400">Seats</span><span>{seats.join(', ')}</span></div>
        <div className="flex justify-between text-sm"><span className="text-zinc-500 dark:text-zinc-400">Price per ticket</span><span>${SEAT_PRICE}</span></div>
        <div className="h-px bg-zinc-200 dark:bg-zinc-800" />
        <div className="flex justify-between text-lg font-bold"><span>Total</span><span>${seats.length * SEAT_PRICE}</span></div>
        <button onClick={confirm} className="mt-1 rounded-lg bg-teal-500 py-3.5 text-sm font-bold text-zinc-950">Confirm Purchase</button>
      </div>
    </div>
  );
}
