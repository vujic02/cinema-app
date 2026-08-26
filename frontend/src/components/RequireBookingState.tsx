import { Navigate, Outlet } from 'react-router-dom';
import { useBooking } from '../context/BookingContext';

type Stage = 'seats' | 'checkout' | 'confirmation';

/**
 * Guards the three routes that only mean anything mid-flow.
 *
 * The handoff exposed /seats, /checkout and /confirmation as plain nav tabs, so clicking
 * Checkout from a cold start rendered an order summary for no movie, with an empty seat list
 * and a $0 total, and Confirm Purchase produced a booking out of nothing. Each stage now states
 * what it needs and sends the visitor back to the step that provides it.
 *
 * Booking state only. Whether the visitor is *signed in* is a separate axis — `RequireAuth`
 * handles that, and the two compose: /checkout needs both a seat and an account.
 */
export default function RequireBookingState({ stage }: { stage: Stage }) {
  const { selectedShowing, selectedSeats, confirmation } = useBooking();

  if (stage === 'confirmation') {
    return confirmation ? <Outlet /> : <Navigate to="/showings" replace />;
  }

  // Both remaining stages need a showing; without one there is nothing to seat.
  if (!selectedShowing) {
    return <Navigate to="/showings" replace />;
  }

  if (stage === 'seats') {
    return <Outlet />;
  }

  return selectedSeats.length > 0 ? <Outlet /> : <Navigate to="/seats" replace />;
}
