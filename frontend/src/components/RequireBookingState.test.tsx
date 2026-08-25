import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { Showtime } from '../types';
import RequireBookingState from './RequireBookingState';

/**
 * The guard's whole job is branching on booking state, so booking state is what the test drives.
 * Mocking the hook rather than wrapping in a real BookingProvider keeps these cases independent
 * of how the provider happens to build that state — Part 7 replaces its internals with TanStack
 * Query and real API calls, and none of the redirect rules below should move when it does.
 *
 * vi.hoisted is required: vi.mock is lifted above the imports, so a plain const declared here
 * would still be in its temporal dead zone when the factory runs.
 */
const booking = vi.hoisted(() => ({
  selectedShowtime: null as Showtime | null,
  selectedSeats: [] as string[],
  confirmation: null as unknown
}));

vi.mock('../context/BookingContext', () => ({
  useBooking: () => booking
}));

const SHOWTIME: Showtime = { venue: 'Screen 1', date: 'Today', time: '7:00 PM' };

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
  booking.selectedShowtime = null;
  booking.selectedSeats = [];
  booking.confirmation = null;
});

describe('RequireBookingState', () => {
  describe('stage="seats"', () => {
    it('bounces to /showings when no showtime has been picked', () => {
      renderGuard('seats', '/seats');

      expect(screen.getByText('landed on /showings')).toBeInTheDocument();
      expect(screen.queryByText('guarded page')).not.toBeInTheDocument();
    });

    it('renders the page once a showtime exists, even with no seats chosen yet', () => {
      booking.selectedShowtime = SHOWTIME;

      renderGuard('seats', '/seats');

      expect(screen.getByText('guarded page')).toBeInTheDocument();
    });
  });

  describe('stage="checkout"', () => {
    it('bounces to /showings when no showtime has been picked', () => {
      renderGuard('checkout', '/checkout');

      expect(screen.getByText('landed on /showings')).toBeInTheDocument();
    });

    it('bounces to /seats when a showtime exists but no seat was selected', () => {
      booking.selectedShowtime = SHOWTIME;

      renderGuard('checkout', '/checkout');

      expect(screen.getByText('landed on /seats')).toBeInTheDocument();
      expect(screen.queryByText('guarded page')).not.toBeInTheDocument();
    });

    it('renders the page once a showtime and at least one seat exist', () => {
      booking.selectedShowtime = SHOWTIME;
      booking.selectedSeats = ['A1'];

      renderGuard('checkout', '/checkout');

      expect(screen.getByText('guarded page')).toBeInTheDocument();
    });
  });

  describe('stage="confirmation"', () => {
    it('bounces to /showings without a confirmation', () => {
      booking.selectedShowtime = SHOWTIME;
      booking.selectedSeats = ['A1'];

      renderGuard('confirmation', '/confirmation');

      expect(screen.getByText('landed on /showings')).toBeInTheDocument();
    });

    /**
     * confirmPurchase does not clear the showtime today, but MyBookingsPage reaches this route
     * through viewBooking(), which sets a confirmation and nothing else. The confirmation branch
     * therefore has to return before the showtime check — this is the case that pins that order.
     */
    it('renders a past booking that has a confirmation but no live showtime', () => {
      booking.confirmation = { ref: 'LUM-12345' };

      renderGuard('confirmation', '/confirmation');

      expect(screen.getByText('guarded page')).toBeInTheDocument();
    });
  });
});
