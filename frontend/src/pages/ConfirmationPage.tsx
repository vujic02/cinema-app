import { useNavigate } from 'react-router-dom';
import { useBooking } from '../context/BookingContext';
import { formatDayAndTime, formatMoney } from '../lib/datetime';
import { posterGradient } from '../lib/poster';

export default function ConfirmationPage() {
  const { confirmation } = useBooking();
  const navigate = useNavigate();

  // RequireBookingState keeps this route unreachable without a confirmation, but the component
  // still has to satisfy the type — and this is the shape a direct render would need.
  if (!confirmation) {
    return <p className="text-center text-sm text-muted">No booking to show yet — complete a checkout first.</p>;
  }

  return (
    <div className="mx-auto max-w-md text-center">
      <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-ok">
        <div className="h-2.5 w-4.5 -translate-y-0.5 translate-x-0.5 rotate-[-45deg] border-b-4 border-l-4 border-ok-ink" />
      </div>
      <h1 className="mb-1 text-xl font-semibold">
        {confirmation.upcoming ? "You're all set!" : 'Your ticket'}
      </h1>
      <p className="mb-7 text-sm text-muted">
        {confirmation.upcoming ? 'Your tickets have been booked' : 'This showing has already played'}
      </p>

      <div className="rounded-2xl border border-dashed border-line-strong bg-surface p-6 text-left">
        <div className="mb-4.5 flex gap-3.5">
          {/* Same duplicate-`style` defect as CheckoutPage: the gradient lost to the inline
              size and the poster came out blank. Sized with classes now (h-19 w-14.5). */}
          <div
            style={posterGradient(confirmation.posterHue)}
            className="flex h-19 w-14.5 flex-shrink-0 items-center justify-center rounded-lg font-mono text-[8px] text-white/50"
          >
            POSTER
          </div>
          <div>
            <h3 className="text-lg font-semibold">{confirmation.movieTitle}</h3>
            <p className="mt-1 text-sm text-muted">
              {confirmation.venueName} · {formatDayAndTime(confirmation.startTime)}
            </p>
            <p className="text-sm text-muted">
              {/* Seats come back sorted by label, so a three-seat ticket reads A4, A5, A6
                  regardless of the order they were clicked. */}
              Seats {confirmation.seats.map(seat => seat.label).join(', ')} ·{' '}
              {formatMoney(confirmation.total)}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-4 border-t border-line pt-4.5">
          {/* TODO: replace with a real QR code (e.g. qrcode.react) encoding the booking ref */}
          <div className="qr-placeholder flex h-19 w-19 flex-shrink-0 items-center justify-center rounded-lg">
            <span className="rounded bg-surface px-1 py-0.5 font-mono text-[9px] text-muted">QR</span>
          </div>
          <div>
            <p className="text-[11px] tracking-wide text-muted">BOOKING REF</p>
            {/* Server-minted (`LUM-` + 6 unambiguous characters). The handoff generated its own
                client-side, which meant two customers could print the same ticket. */}
            <p className="mt-0.5 font-mono text-base font-bold">{confirmation.reference}</p>
          </div>
        </div>
      </div>

      <div className="mt-6 flex gap-2.5">
        <button
          onClick={() => navigate('/bookings')}
          className="flex-1 rounded-lg border border-line-strong py-3 text-sm font-semibold"
        >
          My Bookings
        </button>
        <button
          onClick={() => navigate('/showings')}
          className="flex-1 rounded-lg bg-accent py-3 text-sm font-bold text-accent-ink"
        >
          Back to Showings
        </button>
      </div>
    </div>
  );
}
