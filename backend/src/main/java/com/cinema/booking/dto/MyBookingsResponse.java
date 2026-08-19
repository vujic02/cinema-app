package com.cinema.booking.dto;

import java.util.List;

/**
 * The My Bookings screen, which is an Upcoming / Past tab pair. The split is computed server-side
 * from {@code showings.start_time} so both tabs agree on where "now" is — a client deciding for
 * itself would disagree with the server about a showing starting in the next minute.
 */
public record MyBookingsResponse(List<BookingResponse> upcoming, List<BookingResponse> past) {
}
