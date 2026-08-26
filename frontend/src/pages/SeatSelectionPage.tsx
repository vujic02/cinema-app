import { Fragment, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { useHoldSeat, useReleaseSeat, useSeatMap } from '../api/hooks';
import { SeatView } from '../api/types';
import { ErrorNotice, InlineError, Loading } from '../components/QueryState';
import { useAuth } from '../context/AuthContext';
import { useBooking } from '../context/BookingContext';
import { formatDayAndTime, formatMoney } from '../lib/datetime';

// TODO (Part 8): subscribe to STOMP/SockJS /topic/showings/{showingId} here and merge the
// { showingId, seatId, status } frames into the cached seat map, so another customer's hold
// appears without a refetch. The countdown on `expiresAt` and the auto-release on
// navigate-away belong with it.

export default function SeatSelectionPage() {
  const { selectedShowing, selectedSeats, addSeat, removeSeat, setSeats } = useBooking();
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [seatError, setSeatError] = useState<string | null>(null);

  // RequireBookingState guarantees a showing; the fallback keeps the hooks below unconditional.
  const showingId = selectedShowing?.id ?? null;
  const seatMap = useSeatMap(showingId);
  const hold = useHoldSeat(showingId ?? 0);
  const release = useReleaseSeat(showingId ?? 0);

  /**
   * Adopt the holds the server says this user already has.
   *
   * A hold outlives the page — it is a Redis key with a TTL, not component state — so a reload
   * or a trip through the login form comes back to seats that are still ours. `heldByYou` on
   * the authenticated seat map is how the server reports them; without this the seats would
   * render as selected but the checkout guard would see an empty list.
   *
   * Once per showing: after the first reconcile, local clicks are the source of truth and a
   * refetch must not resurrect a seat the customer just released.
   */
  const reconciledFor = useRef<number | null>(null);
  useEffect(() => {
    if (!seatMap.data || showingId === null || reconciledFor.current === showingId) return;
    reconciledFor.current = showingId;

    const mine = seatMap.data.rows
      .flatMap(row => row.seats)
      .filter(seat => seat.heldByYou)
      .map(seat => ({ seatId: seat.seatId, label: seat.label, expiresAt: null }));

    if (mine.length > 0) setSeats(mine);
  }, [seatMap.data, showingId, setSeats]);

  async function toggleSeat(seat: SeatView) {
    setSeatError(null);

    // Holding a seat needs a token (`SecurityConfig` permits only GET under /api/showings/**).
    // Browsing the map anonymously is fine, so the prompt comes at the click, not the page —
    // and `from` brings them straight back here with the showing still selected.
    if (!isAuthenticated) {
      navigate('/login', { state: { from: location } });
      return;
    }

    try {
      if (seat.heldByYou) {
        await release.mutateAsync(seat.seatId);
        removeSeat(seat.seatId);
      } else {
        const held = await hold.mutateAsync(seat.seatId);
        addSeat({ seatId: held.seatId, label: held.label, expiresAt: held.expiresAt });
      }
    } catch (error) {
      // Losing the race is the expected outcome of a contended seat, not a crash: the mutation
      // has already refetched the map, so the seat is about to repaint with the truth.
      setSeatError(errorMessage(error, 'That seat is no longer available.'));
      removeSeat(seat.seatId);
    }
  }

  if (seatMap.isPending) return <Loading label="Loading the seat map…" />;
  if (seatMap.isError) {
    return (
      <div className="mx-auto max-w-3xl">
        <ErrorNotice
          error={seatMap.error}
          fallback="Could not load the seat map."
          onRetry={() => seatMap.refetch()}
        />
      </div>
    );
  }

  const map = seatMap.data;
  const count = selectedSeats.length;
  const total = count * map.price;
  const busy = hold.isPending || release.isPending;

  return (
    <div className="mx-auto max-w-3xl">
      <div className="mb-5">
        <h1 className="text-2xl font-semibold">{map.movieTitle}</h1>
        <p className="mt-1 text-sm text-muted">
          {map.venueName} · {formatDayAndTime(map.startTime)} · {formatMoney(map.price)} per ticket
        </p>
      </div>

      {seatError && (
        <div className="mb-4">
          <InlineError message={seatError} />
        </div>
      )}

      <div className="overflow-x-auto rounded-2xl border border-line bg-surface px-5 py-8">
        <div className="mb-8 flex justify-center">
          <div className="h-2.5 w-64 rounded-t-full border-t-4 border-accent/70" />
        </div>
        <p className="-mt-6 mb-7 text-center text-xs tracking-[3px] text-muted">SCREEN</p>

        <div className="flex min-w-[460px] flex-col items-center gap-2.5">
          {map.rows.map(row => (
            <div key={row.rowLabel} className="flex items-center gap-2">
              <div className="w-4 text-center text-xs text-muted">{row.rowLabel}</div>
              {row.seats.map(seat => {
                // Four states now, where the mock data had three. `heldByYou` is what separates
                // "mine" from "somebody else's", and it is the one user-specific field the API
                // exposes — it never appears on the shared WebSocket topic.
                const mine = seat.heldByYou;
                const taken = !mine && seat.status !== 'AVAILABLE';

                // Available is a light seat with a border, per the handoff README. The bundle
                // shipped `bg-zinc-200 dark:bg-zinc-100` and no border, which read as greyed
                // out — the same "disabled" grey the proceed button uses.
                const seatClass = mine
                  ? 'bg-seat-selected text-white cursor-pointer'
                  : seat.status === 'SOLD'
                    ? 'bg-seat-reserved text-white cursor-not-allowed opacity-85'
                    : seat.status === 'HELD'
                      ? 'bg-seat-held text-white cursor-not-allowed opacity-85'
                      : 'bg-seat-available text-seat-ink border border-line-strong cursor-pointer';

                return (
                  <Fragment key={seat.seatId}>
                    <button
                      onClick={() => toggleSeat(seat)}
                      disabled={taken || busy}
                      aria-label={`Seat ${seat.label}${
                        seat.status === 'SOLD' ? ' (sold)' : seat.status === 'HELD' && !mine ? ' (on hold)' : ''
                      }`}
                      aria-pressed={mine}
                      className={`flex h-7.5 w-7.5 flex-shrink-0 items-center justify-center rounded-md text-xs font-bold disabled:opacity-60 ${seatClass}`}
                    >
                      {seat.seatNumber}
                    </button>
                    {seat.aisleGap && <div className="w-4" />}
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
          <span className="h-3.5 w-3.5 rounded bg-seat-held" />
          On hold
        </div>
        <div className="flex items-center gap-2">
          <span className="h-3.5 w-3.5 rounded bg-seat-reserved" />
          Sold
        </div>
        <div className="flex items-center gap-2">
          <span className="h-3.5 w-3.5 rounded bg-seat-selected" />
          Yours
        </div>
      </div>

      <div className="mt-7 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-line bg-surface px-5 py-4">
        <div className="text-sm">
          {count
            ? `${count} seat${count > 1 ? 's' : ''} selected · ${formatMoney(total)}`
            : isAuthenticated
              ? 'Select your seats'
              : 'Pick a seat to sign in and hold it'}
        </div>
        <button
          disabled={count === 0 || busy}
          onClick={() => navigate('/checkout')}
          className={`rounded-lg px-5 py-3 text-sm font-bold ${
            count && !busy ? 'bg-accent text-accent-ink' : 'cursor-not-allowed bg-sunken text-disabled'
          }`}
        >
          Proceed to Checkout
        </button>
      </div>
    </div>
  );
}
