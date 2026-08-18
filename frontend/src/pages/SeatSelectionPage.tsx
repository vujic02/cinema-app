import { Fragment } from 'react';
import { useNavigate } from 'react-router-dom';
import { VENUE_LAYOUT, SEAT_PRICE } from '../data/mockData';
import { useBooking } from '../context/BookingContext';

// TODO: subscribe to STOMP/SockJS /topic/showings/{id} here (Part 8) and merge the
// { seatId, status } frames into the same map this reads, so other customers' holds appear
// live while local selection stays optimistic.

export default function SeatSelectionPage() {
  const { selectedMovie, selectedShowtime, selectedSeats, toggleSeat } = useBooking();
  const navigate = useNavigate();

  const count = selectedSeats.length;
  const seatNumbers = Array.from({ length: VENUE_LAYOUT.seatsPerRow }, (_, i) => i + 1);

  return (
    <div className="mx-auto max-w-3xl">
      <div className="mb-5">
        <h1 className="text-2xl font-semibold">{selectedMovie?.title ?? 'Select a showing'}</h1>
        <p className="mt-1 text-sm text-muted">
          {selectedShowtime
            ? `${selectedShowtime.venue} · ${selectedShowtime.date}, ${selectedShowtime.time}`
            : 'Pick a showtime from Showings first'}
        </p>
      </div>

      <div className="overflow-x-auto rounded-2xl border border-line bg-surface px-5 py-8">
        <div className="mb-8 flex justify-center">
          <div className="h-2.5 w-64 rounded-t-full border-t-4 border-accent/70" />
        </div>
        <p className="-mt-6 mb-7 text-center text-xs tracking-[3px] text-muted">SCREEN</p>

        <div className="flex min-w-[460px] flex-col items-center gap-2.5">
          {VENUE_LAYOUT.rows.map(row => (
            <div key={row} className="flex items-center gap-2">
              <div className="w-4 text-center text-xs text-muted">{row}</div>
              {seatNumbers.map(num => {
                const id = `${row}${num}`;
                const isReserved = VENUE_LAYOUT.reservedSeatIds.includes(id);
                const isSelected = selectedSeats.includes(id);

                // Available is a light seat with a border, per the handoff README. The bundle
                // shipped `bg-zinc-200 dark:bg-zinc-100` and no border, which read as greyed
                // out — the same "disabled" grey the proceed button uses.
                const seatClass = isReserved
                  ? 'bg-seat-reserved text-white cursor-not-allowed opacity-85'
                  : isSelected
                    ? 'bg-seat-selected text-white cursor-pointer'
                    : 'bg-seat-available text-seat-ink border border-line-strong cursor-pointer';

                return (
                  <Fragment key={id}>
                    {num === VENUE_LAYOUT.aisleAfterSeat + 1 && <div className="w-4" />}
                    <button
                      onClick={() => toggleSeat(id)}
                      disabled={isReserved}
                      aria-label={`Seat ${id}${isReserved ? ' (reserved)' : ''}`}
                      aria-pressed={isSelected}
                      className={`flex h-7.5 w-7.5 flex-shrink-0 items-center justify-center rounded-md text-xs font-bold ${seatClass}`}
                    >
                      {num}
                    </button>
                  </Fragment>
                );
              })}
            </div>
          ))}
        </div>
      </div>

      <div className="mt-5 flex flex-wrap justify-center gap-6 text-sm text-muted">
        <div className="flex items-center gap-2">
          <span className="h-3.5 w-3.5 rounded border border-line-strong bg-seat-available" />
          Available
        </div>
        <div className="flex items-center gap-2">
          <span className="h-3.5 w-3.5 rounded bg-seat-reserved" />
          Reserved
        </div>
        <div className="flex items-center gap-2">
          <span className="h-3.5 w-3.5 rounded bg-seat-selected" />
          Selected
        </div>
      </div>

      <div className="mt-7 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-line bg-surface px-5 py-4">
        <div className="text-sm">
          {count ? `${count} seat${count > 1 ? 's' : ''} selected · $${count * SEAT_PRICE}` : 'Select your seats'}
        </div>
        <button
          disabled={count === 0}
          onClick={() => navigate('/checkout')}
          className={`rounded-lg px-5 py-3 text-sm font-bold ${
            count ? 'bg-accent text-accent-ink' : 'cursor-not-allowed bg-sunken text-disabled'
          }`}
        >
          Proceed to Checkout
        </button>
      </div>
    </div>
  );
}
