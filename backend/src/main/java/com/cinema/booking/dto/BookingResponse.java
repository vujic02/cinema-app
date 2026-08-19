package com.cinema.booking.dto;

import com.cinema.booking.domain.Booking;
import com.cinema.booking.domain.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * One purchase, not one row. A three-seat booking is three {@code bookings} rows sharing a
 * reference; the customer thinks of it as a single ticket, so it is assembled back into one
 * object here.
 *
 * @param customer populated only on the admin views. Jackson is configured to drop nulls, so it
 *                 does not appear at all on a customer's own bookings.
 */
public record BookingResponse(
        String reference,
        Long showingId,
        String movieTitle,
        String venueName,
        Instant startTime,
        List<SeatSummary> seats,
        BigDecimal total,
        BookingStatus status,
        boolean upcoming,
        Instant bookedAt,
        CustomerSummary customer
) {

    public record SeatSummary(Long seatId, String label) {
    }

    public record CustomerSummary(Long id, String email, String fullName) {
    }

    /**
     * Folds the rows of one purchase into a single response.
     * <p>
     * Every association it touches — showing, movie, venue, seat, row — has to be initialised
     * already; the repository queries that feed this all fetch-join them, because doing it
     * lazily would be one query per seat on a screen that lists many bookings.
     */
    public static BookingResponse of(List<Booking> rows, Instant now, boolean includeCustomer) {
        Booking first = rows.get(0);

        List<SeatSummary> seats = rows.stream()
                .map(row -> new SeatSummary(row.getSeat().getId(), row.getSeat().label()))
                // Seat order follows the auditorium, not the order the customer happened to
                // click, so a ticket reads A4, A5, A6.
                .sorted(Comparator.comparing(SeatSummary::label))
                .toList();

        BigDecimal total = rows.stream()
                .map(Booking::getPricePaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Earliest row wins: all rows of a purchase are written in one transaction, so this is
        // stable regardless of the order they come back in.
        Instant bookedAt = rows.stream()
                .map(Booking::getCreatedAt)
                .min(Comparator.naturalOrder())
                .orElse(first.getCreatedAt());

        return new BookingResponse(
                first.getBookingReference(),
                first.getShowing().getId(),
                first.getShowing().getMovie().getTitle(),
                first.getShowing().getVenue().getName(),
                first.getShowing().getStartTime(),
                seats,
                total,
                first.getStatus(),
                first.getShowing().getStartTime().isAfter(now),
                bookedAt,
                includeCustomer
                        ? new CustomerSummary(
                                first.getUser().getId(),
                                first.getUser().getEmail(),
                                first.getUser().getFullName())
                        : null);
    }
}
