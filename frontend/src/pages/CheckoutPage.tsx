import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { errorCode, errorMessage } from '../api/client';
import { useConfirmBooking } from '../api/hooks';
import { InlineError } from '../components/QueryState';
import { useBooking } from '../context/BookingContext';
import { formatDayAndTime, formatMoney } from '../lib/datetime';
import { Poster } from '../components/Poster';

/**
 * Codes where the seats themselves are the problem, so the only useful next step is back to the
 * seat map. Anything else — a network blip, a 500 — leaves the holds intact and is worth
 * retrying from here.
 */
const SEND_BACK_TO_SEATS = new Set(['HOLD_EXPIRED', 'SEAT_HELD', 'SEAT_SOLD', 'SHOWING_STARTED']);

export default function CheckoutPage() {
  const { selectedShowing, selectedSeats, setConfirmation } = useBooking();
  const confirmBooking = useConfirmBooking();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const [seatsLost, setSeatsLost] = useState(false);

  // Guaranteed by RequireBookingState; the guards keep this from being a real branch.
  if (!selectedShowing) return null;

  const price = selectedShowing.price;
  const labels = selectedSeats.map(seat => seat.label).sort();

  async function confirm() {
    if (!selectedShowing) return;
    setError(null);
    setSeatsLost(false);

    try {
      // No price and no total in the request — the server reads `showings.price` and snapshots
      // it onto every row, so what the customer is charged is never what the client claimed.
      const booking = await confirmBooking.mutateAsync({
        showingId: selectedShowing.id,
        seatIds: selectedSeats.map(seat => seat.seatId)
      });
      setConfirmation(booking);
      navigate('/confirmation');
    } catch (caught) {
      setError(errorMessage(caught, 'Could not complete the purchase.'));
      setSeatsLost(SEND_BACK_TO_SEATS.has(errorCode(caught) ?? ''));
    }
  }

  return (
    <div className="mx-auto max-w-lg">
      <h1 className="mb-5 text-2xl font-semibold">Order Summary</h1>
      <div className="flex flex-col gap-4 rounded-2xl border border-line bg-surface p-6">
        <div className="flex gap-4">
          {/* The handoff put `style={posterGradient(...)}` and `style={{ width, height }}` on
              one element here. JSX keeps only the last of a repeated attribute, so the gradient
              was silently dropped and the poster rendered as a blank box. Sizing lives in a
              class now, which is what the markup was reaching for anyway. */}
          <Poster
            posterUrl={selectedShowing.movie.posterUrl}
            posterHue={selectedShowing.movie.posterHue}
            title={selectedShowing.movie.title}
            className="h-24 w-16 rounded-lg text-sm"
          />
          <div>
            <h3 className="text-lg font-semibold">{selectedShowing.movie.title}</h3>
            <p className="mt-1.5 text-sm text-muted">
              {selectedShowing.venue.name} · {formatDayAndTime(selectedShowing.startTime)}
            </p>
          </div>
        </div>
        <div className="h-px bg-line" />
        <div className="flex justify-between text-sm">
          <span className="text-muted">Seats</span>
          <span>{labels.join(', ')}</span>
        </div>
        <div className="flex justify-between text-sm">
          <span className="text-muted">Price per ticket</span>
          <span>{formatMoney(price)}</span>
        </div>
        <div className="h-px bg-line" />
        <div className="flex justify-between text-lg font-bold">
          <span>Total</span>
          {/* The authoritative total is the one on the booking the server returns; this is the
              same arithmetic done locally so the customer sees a number before they commit. */}
          <span>{formatMoney(selectedSeats.length * price)}</span>
        </div>

        {error && <InlineError message={error} />}

        {seatsLost ? (
          <button
            onClick={() => navigate('/seats')}
            className="mt-1 rounded-lg bg-accent py-3.5 text-sm font-bold text-accent-ink"
          >
            Back to Seat Map
          </button>
        ) : (
          <button
            onClick={confirm}
            disabled={confirmBooking.isPending}
            className="mt-1 rounded-lg bg-accent py-3.5 text-sm font-bold text-accent-ink disabled:cursor-not-allowed disabled:bg-sunken disabled:text-disabled"
          >
            {confirmBooking.isPending ? 'Confirming…' : 'Confirm Purchase'}
          </button>
        )}
      </div>
    </div>
  );
}
