import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { posterGradient } from '../data/mockData';
import { useBooking } from '../context/BookingContext';
import { Booking, BookingStatus } from '../types';

export default function MyBookingsPage() {
  const [tab, setTab] = useState<BookingStatus>('upcoming');
  const { bookings, viewBooking } = useBooking();
  const navigate = useNavigate();
  const filtered = bookings.filter(b => b.status === tab);

  function open(booking: Booking) {
    viewBooking(booking);
    navigate('/confirmation');
  }

  return (
    <div>
      <h1 className="mb-5 text-3xl font-semibold">My Bookings</h1>
      <div className="mb-6 flex w-fit gap-1 rounded-lg bg-sunken p-1">
        <button
          onClick={() => setTab('upcoming')}
          className={`rounded-md px-4 py-2 text-sm font-bold ${tab === 'upcoming' ? 'bg-elevated' : 'text-muted'}`}
        >
          Upcoming
        </button>
        <button
          onClick={() => setTab('past')}
          className={`rounded-md px-4 py-2 text-sm font-bold ${tab === 'past' ? 'bg-elevated' : 'text-muted'}`}
        >
          Past
        </button>
      </div>

      <div className="flex flex-col gap-3.5">
        {filtered.map(b => (
          <div
            key={b.id}
            className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-line bg-surface px-5 py-4"
          >
            <div className="flex items-center gap-3.5">
              <div
                style={posterGradient(b.hue)}
                className="flex h-16 w-12 flex-shrink-0 items-center justify-center rounded-lg font-mono text-[7px] text-white/50"
              >
                POSTER
              </div>
              <div>
                <h3 className="text-base font-semibold">{b.movieTitle}</h3>
                <p className="mt-1 text-sm text-muted">
                  {b.venue} · {b.date}, {b.time} · {b.seats.length} seat{b.seats.length > 1 ? 's' : ''}
                </p>
              </div>
            </div>
            <div className="flex items-center gap-3.5">
              <span
                className={`rounded-full px-2.5 py-1 text-xs font-bold ${
                  b.status === 'upcoming' ? 'bg-ok text-ok-ink' : 'bg-sunken text-muted'
                }`}
              >
                {b.status === 'upcoming' ? 'Upcoming' : 'Completed'}
              </span>
              <button
                onClick={() => open(b)}
                className="rounded-lg border border-line-strong px-4 py-2 text-sm font-semibold"
              >
                View Ticket
              </button>
            </div>
          </div>
        ))}
        {filtered.length === 0 && <p className="py-10 text-center text-sm text-muted">No {tab} bookings.</p>}
      </div>
    </div>
  );
}
