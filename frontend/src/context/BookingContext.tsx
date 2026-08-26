import React, { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { BookingResponse, ShowingResponse } from '../api/types';

/**
 * The state that spans the three steps of the booking flow, and nothing else.
 *
 * Before Part 7 this context *was* the application: it owned the seat map's reserved list, the
 * booking history, and minted its own booking reference. All three are server state now and
 * live in TanStack Query. What is left is genuinely client state — which showing the customer
 * clicked, which seats they are currently holding, and the receipt to render next — none of
 * which the server can be asked for by URL.
 *
 * Seats are the seats the customer *holds*. A hold is a Redis key with a TTL placed by
 * `POST /showings/{id}/seats/{seatId}/hold`, so this list mirrors server state rather than
 * defining it; `SeatSelectionPage` reconciles it against the seat map's `heldByYou` flags on
 * load, which is what makes a reload mid-selection resume instead of losing the seats.
 */

export interface HeldSeat {
  seatId: number;
  /** "C4" — carried alongside the id so checkout can print seats without another lookup. */
  label: string;
  /** ISO instant the hold lapses. Part 8's countdown reads this; Part 7 only stores it. */
  expiresAt: string | null;
}

interface BookingContextValue {
  selectedShowing: ShowingResponse | null;
  selectedSeats: HeldSeat[];
  confirmation: BookingResponse | null;
  selectShowing: (showing: ShowingResponse) => void;
  addSeat: (seat: HeldSeat) => void;
  removeSeat: (seatId: number) => void;
  /** Replaces the working set wholesale — used to adopt the holds the server says we have. */
  setSeats: (seats: HeldSeat[]) => void;
  setConfirmation: (booking: BookingResponse) => void;
  /** Drops the in-flight flow, leaving any confirmation on screen alone. */
  clearFlow: () => void;
}

const BookingContext = createContext<BookingContextValue | undefined>(undefined);

export function BookingProvider({ children }: { children: React.ReactNode }) {
  const [selectedShowing, setSelectedShowing] = useState<ShowingResponse | null>(null);
  const [selectedSeats, setSelectedSeats] = useState<HeldSeat[]>([]);
  const [confirmation, setConfirmationState] = useState<BookingResponse | null>(null);

  const selectShowing = useCallback((showing: ShowingResponse) => {
    setSelectedShowing(showing);
    // Holds are per showing; seats picked for a different screening mean nothing here. The
    // abandoned ones lapse on their own TTL — Part 8 releases them eagerly on navigate-away.
    setSelectedSeats([]);
  }, []);

  const addSeat = useCallback((seat: HeldSeat) => {
    setSelectedSeats(seats => (seats.some(s => s.seatId === seat.seatId) ? seats : [...seats, seat]));
  }, []);

  const removeSeat = useCallback((seatId: number) => {
    setSelectedSeats(seats => seats.filter(seat => seat.seatId !== seatId));
  }, []);

  const setSeats = useCallback((seats: HeldSeat[]) => setSelectedSeats(seats), []);

  const setConfirmation = useCallback((booking: BookingResponse) => {
    setConfirmationState(booking);
    // The purchase consumed the holds; leaving them selected would let the customer walk back
    // into checkout with seats they have already bought.
    setSelectedSeats([]);
  }, []);

  const clearFlow = useCallback(() => {
    setSelectedShowing(null);
    setSelectedSeats([]);
  }, []);

  const value = useMemo<BookingContextValue>(
    () => ({
      selectedShowing,
      selectedSeats,
      confirmation,
      selectShowing,
      addSeat,
      removeSeat,
      setSeats,
      setConfirmation,
      clearFlow
    }),
    [selectedShowing, selectedSeats, confirmation, selectShowing, addSeat, removeSeat, setSeats, setConfirmation, clearFlow]
  );

  return <BookingContext.Provider value={value}>{children}</BookingContext.Provider>;
}

export function useBooking() {
  const ctx = useContext(BookingContext);
  if (!ctx) throw new Error('useBooking must be used within BookingProvider');
  return ctx;
}
