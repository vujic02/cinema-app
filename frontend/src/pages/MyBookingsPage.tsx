import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMyBookings } from '../api/hooks';
import { BookingResponse } from '../api/types';
import { ErrorNotice, Loading } from '../components/QueryState';
import { useBooking } from '../context/BookingContext';
import { formatDayAndTime } from '../lib/datetime';
import { Poster } from '../components/Poster';

type Tab = 'upcoming' | 'past';

export default function MyBookingsPage() {
  const [tab, setTab] = useState<Tab>('upcoming');
  const { setConfirmation } = useBooking();
  const bookings = useMyBookings();
  const navigate = useNavigate();

  /**
   * The upcoming/past split is the server's, not a filter applied here: it is computed from
   * `showings.start_time` against the server's clock, so both tabs agree on where "now" is even
   * for a showing starting in the next minute.
   */
  const visible = bookings.data?.[tab] ?? [];

  function open(booking: BookingResponse) {
    setConfirmation(booking);
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

      {bookings.isPending ? (
        <Loading label="Loading your bookings…" />
      ) : bookings.isError ? (
        <ErrorNotice
          error={bookings.error}
          fallback="Could not load your bookings."
          onRetry={() => bookings.refetch()}
        />
      ) : (
        <div className="flex flex-col gap-3.5">
          {visible.map(booking => (
            <div
              key={booking.reference}
              className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-line bg-surface px-5 py-4"
            >
              <div className="flex items-center gap-3.5">
                <Poster
                  posterUrl={booking.posterUrl}
                  posterHue={booking.posterHue}
                  title={booking.movieTitle}
                  className="h-18 w-12 rounded-lg text-xs"
                />
                <div>
                  <h3 className="text-base font-semibold">{booking.movieTitle}</h3>
                  <p className="mt-1 text-sm text-muted">
                    {booking.venueName} · {formatDayAndTime(booking.startTime)} · {booking.seats.length} seat
                    {booking.seats.length > 1 ? 's' : ''}
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-3.5">
                <span
                  className={`rounded-full px-2.5 py-1 text-xs font-bold ${
                    booking.upcoming ? 'bg-ok text-ok-ink' : 'bg-sunken text-muted'
                  }`}
                >
                  {booking.upcoming ? 'Upcoming' : 'Completed'}
                </span>
                <button
                  onClick={() => open(booking)}
                  className="rounded-lg border border-line-strong px-4 py-2 text-sm font-semibold"
                >
                  View Ticket
                </button>
              </div>
            </div>
          ))}
          {visible.length === 0 && <p className="py-10 text-center text-sm text-muted">No {tab} bookings.</p>}
        </div>
      )}
    </div>
  );
}
