import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ShowingResponse } from '../api/types';
import { HeldSeat } from '../context/BookingContext';
import RequireBookingState from './RequireBookingState';

/**
 * The guard's whole job is branching on booking state, so booking state is what the test drives.
 * Mocking the hook rather than wrapping in a real BookingProvider keeps these cases independent
 * of how the provider happens to build that state — Part 7 replaced its internals with TanStack
 * Query and real API calls, and none of the redirect rules below moved when it did.
 *
 * vi.hoisted is required: vi.mock is lifted above the imports, so a plain const declared here
 * would still be in its temporal dead zone when the factory runs.
 */
const booking = vi.hoisted(() => ({
  selectedShowing: null as unknown,
  selectedSeats: [] as HeldSeat[],
  confirmation: null as unknown
}));

vi.mock('../context/BookingContext', () => ({
  useBooking: () => booking
}));

const SHOWING = {
  id: 42,
  startTime: '2026-08-26T19:00:00Z',
  price: 14,
  movie: { id: 1, title: 'Comet Line', durationMinutes: 132, genre: 'Sci-Fi', rating: 'PG-13', posterHue: 200 },
  venue: { id: 3, name: 'Screen 1' }
} satisfies ShowingResponse;

const SEAT: HeldSeat = { seatId: 101, label: 'A1', expiresAt: null };

type Stage = 'seats' | 'checkout' | 'confirmation';

/**
 * Mounts the guard the way App.tsx does — as a layout route with the real page as its Outlet —
 * and gives the redirect targets something to land on so <Navigate> is observable.
 */
function renderGuard(stage: Stage, path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<RequireBookingState stage={stage} />}>
          <Route path={path} element={<div>guarded page</div>} />
        </Route>
        {['/showings', '/seats']
          .filter(target => target !== path)
          .map(target => (
            <Route key={target} path={target} element={<div>{`landed on ${target}`}</div>} />
          ))}
      </Routes>
    </MemoryRouter>
  );
}

beforeEach(() => {
  booking.selectedShowing = null;
  booking.selectedSeats = [];
  booking.confirmation = null;
});

describe('RequireBookingState', () => {
  describe('stage="seats"', () => {
    it('bounces to /showings when no showing has been picked', () => {
      renderGuard('seats', '/seats');

      expect(screen.getByText('landed on /showings')).toBeInTheDocument();
      expect(screen.queryByText('guarded page')).not.toBeInTheDocument();
    });

    it('renders the page once a showing exists, even with no seats chosen yet', () => {
      booking.selectedShowing = SHOWING;

      renderGuard('seats', '/seats');

      expect(screen.getByText('guarded page')).toBeInTheDocument();
    });
  });

  describe('stage="checkout"', () => {
    it('bounces to /showings when no showing has been picked', () => {
      renderGuard('checkout', '/checkout');

      expect(screen.getByText('landed on /showings')).toBeInTheDocument();
    });

    it('bounces to /seats when a showing exists but no seat is held', () => {
      booking.selectedShowing = SHOWING;

      renderGuard('checkout', '/checkout');

      expect(screen.getByText('landed on /seats')).toBeInTheDocument();
      expect(screen.queryByText('guarded page')).not.toBeInTheDocument();
    });

    it('renders the page once a showing and at least one held seat exist', () => {
      booking.selectedShowing = SHOWING;
      booking.selectedSeats = [SEAT];

      renderGuard('checkout', '/checkout');

      expect(screen.getByText('guarded page')).toBeInTheDocument();
    });
  });

  describe('stage="confirmation"', () => {
    it('bounces to /showings without a confirmation', () => {
      booking.selectedShowing = SHOWING;
      booking.selectedSeats = [SEAT];

      renderGuard('confirmation', '/confirmation');

      expect(screen.getByText('landed on /showings')).toBeInTheDocument();
    });

    /**
     * Confirming a purchase clears the held seats, and MyBookingsPage reaches this route through
     * viewBooking(), which sets a confirmation and nothing else. The confirmation branch
     * therefore has to return before the showing check — this is the case that pins that order.
     */
    it('renders a past booking that has a confirmation but no live showing', () => {
      booking.confirmation = { reference: 'LUM-12345' };

      renderGuard('confirmation', '/confirmation');

      expect(screen.getByText('guarded page')).toBeInTheDocument();
    });
  });
});
