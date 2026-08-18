package com.cinema.seat.repository;

import com.cinema.venue.domain.SeatType;

/**
 * One seat of one showing, flattened: its position, its label parts, and whether a sold booking
 * exists against it. Populated by a JPQL constructor expression, so the seat map costs no entity
 * hydration and no lazy loading.
 *
 * @param soldBookingId id of the SOLD booking on this seat, or {@code null} when there is none
 */
public record SeatMapEntry(
        int rowIndex,
        String rowLabel,
        Long seatId,
        int seatNumber,
        SeatType seatType,
        boolean aisleGap,
        Long soldBookingId
) {

    public boolean sold() {
        return soldBookingId != null;
    }

    /** The customer-facing identifier, e.g. "C4" — the same rule as {@code Seat.label()}. */
    public String label() {
        return rowLabel + seatNumber;
    }
}
