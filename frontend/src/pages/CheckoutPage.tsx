import { useNavigate } from 'react-router-dom';
import { SEAT_PRICE, posterGradient } from '../data/mockData';
import { useBooking } from '../context/BookingContext';

export default function CheckoutPage() {
  const { selectedMovie, selectedShowtime, selectedSeats, confirmPurchase } = useBooking();
  const navigate = useNavigate();

  function confirm() {
    confirmPurchase();
    navigate('/confirmation');
  }

  return (
    <div className="mx-auto max-w-lg">
      <h1 className="mb-5 text-2xl font-semibold">Order Summary</h1>
      <div className="flex flex-col gap-4 rounded-2xl border border-line bg-surface p-6">
        <div className="flex gap-4">
          {/* The handoff put `style={posterGradient(...)}` and `style={{ width, height }}` on
              this one element. JSX keeps only the last of a repeated attribute, so the gradient
              was silently dropped and the poster rendered as a blank box. The size is a class
              now (h-21 w-16 = 84x64), which is what the markup was reaching for anyway. */}
          <div
            style={posterGradient(selectedMovie?.hue ?? 0)}
            className="flex h-21 w-16 flex-shrink-0 items-center justify-center rounded-lg font-mono text-[8px] text-white/50"
          >
            POSTER
          </div>
          <div>
            <h3 className="text-lg font-semibold">{selectedMovie?.title}</h3>
            <p className="mt-1.5 text-sm text-muted">
              {selectedShowtime
                ? `${selectedShowtime.venue} · ${selectedShowtime.date}, ${selectedShowtime.time}`
                : ''}
            </p>
          </div>
        </div>
        <div className="h-px bg-line" />
        <div className="flex justify-between text-sm">
          <span className="text-muted">Seats</span>
          <span>{selectedSeats.join(', ')}</span>
        </div>
        <div className="flex justify-between text-sm">
          <span className="text-muted">Price per ticket</span>
          <span>${SEAT_PRICE}</span>
        </div>
        <div className="h-px bg-line" />
        <div className="flex justify-between text-lg font-bold">
          <span>Total</span>
          <span>${selectedSeats.length * SEAT_PRICE}</span>
        </div>
        <button
          onClick={confirm}
          className="mt-1 rounded-lg bg-accent py-3.5 text-sm font-bold text-accent-ink"
        >
          Confirm Purchase
        </button>
      </div>
    </div>
  );
}
