import React from 'react';
import { useNavigate } from 'react-router-dom';
import { VENUE_LAYOUT, SEAT_PRICE } from '../data/mockData';
import { useBooking } from '../context/BookingContext';

export default function SeatSelectionPage() {
  const { selectedMovie, selectedShowtime, selectedSeats, toggleSeat } = useBooking();
  const navigate = useNavigate();
  const movie = selectedMovie;
  const showtime = selectedShowtime;

  const count = selectedSeats.length;

  return (
    <div className="mx-auto max-w-3xl">
      <div className="mb-5">
        <h1 className="text-2xl font-semibold">{movie?.title ?? 'Select a showing'}</h1>
        <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
          {showtime ? `${showtime.venue} · ${showtime.date}, ${showtime.time}` : 'Pick a showtime from Showings first'}
        </p>
      </div>

      <div className="overflow-x-auto rounded-2xl border border-zinc-200 bg-zinc-50 px-5 py-8 dark:border-zinc-800 dark:bg-zinc-900">
        <div className="mb-8 flex justify-center">
          <div className="h-2.5 w-64 rounded-t-full border-t-4 border-teal-500/70" />
        </div>
        <p className="-mt-6 mb-7 text-center text-xs tracking-[3px] text-zinc-500 dark:text-zinc-400">SCREEN</p>

        <div className="flex min-w-[460px] flex-col items-center gap-2.5">
          {VENUE_LAYOUT.rows.map(row => (
            <div key={row} className="flex items-center gap-2">
              <div className="w-4 text-center text-xs text-zinc-500 dark:text-zinc-400">{row}</div>
              {Array.from({ length: VENUE_LAYOUT.seatsPerRow }, (_, i) => i + 1).map(num => {
                const id = `${row}${num}`;
                const isReserved = VENUE_LAYOUT.reservedSeatIds.includes(id);
                const isSelected = selectedSeats.includes(id);
                const seatClass = isReserved
                  ? 'bg-red-500 text-white cursor-not-allowed opacity-85'
                  : isSelected
                  ? 'bg-emerald-500 text-white cursor-pointer'
                  : 'bg-zinc-200 text-zinc-900 dark:bg-zinc-100 cursor-pointer';
                return (
                  <React.Fragment key={id}>
                    {num === VENUE_LAYOUT.aisleAfterSeat + 1 && <div className="w-4" />}
                    <button
                      onClick={() => toggleSeat(id)}
                      disabled={isReserved}
                      className={`flex h-7.5 w-7.5 flex-shrink-0 items-center justify-center rounded-md text-xs font-bold ${seatClass}`}
                      style={{ width: 30, height: 30 }}
                    >
                      {num}
                    </button>
                  </React.Fragment>
                );
              })}
            </div>
          ))}
        </div>
      </div>

      <div className="mt-5 flex flex-wrap justify-center gap-6 text-sm text-zinc-500 dark:text-zinc-400">
        <div className="flex items-center gap-2"><span className="h-3.5 w-3.5 rounded bg-zinc-200 dark:bg-zinc-100" />Available</div>
        <div className="flex items-center gap-2"><span className="h-3.5 w-3.5 rounded bg-red-500" />Reserved</div>
        <div className="flex items-center gap-2"><span className="h-3.5 w-3.5 rounded bg-emerald-500" />Selected</div>
      </div>

      <div className="mt-7 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-zinc-200 bg-zinc-50 px-5 py-4 dark:border-zinc-800 dark:bg-zinc-900">
        <div className="text-sm">{count ? `${count} seat${count > 1 ? 's' : ''} selected · $${count * SEAT_PRICE}` : 'Select your seats'}</div>
        <button
          disabled={count === 0}
          onClick={() => navigate('/checkout')}
          className={`rounded-lg px-5 py-3 text-sm font-bold ${count ? 'bg-teal-500 text-zinc-950' : 'cursor-not-allowed bg-zinc-200 text-zinc-400 dark:bg-zinc-800 dark:text-zinc-600'}`}
        >
          Proceed to Checkout
        </button>
      </div>
    </div>
  );
}
