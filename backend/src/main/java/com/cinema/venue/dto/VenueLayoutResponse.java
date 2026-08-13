package com.cinema.venue.dto;

import com.cinema.venue.domain.Seat;
import com.cinema.venue.domain.SeatType;
import com.cinema.venue.domain.Venue;
import com.cinema.venue.domain.VenueRow;

import java.util.List;

/** The full seating plan of a venue, ordered exactly as it should be drawn. */
public record VenueLayoutResponse(
        Long venueId,
        String venueName,
        long seatCount,
        List<RowView> rows
) {

    public record RowView(Long id, int rowIndex, String rowLabel, int seatCount, List<SeatView> seats) {
    }

    /**
     * @param label    customer-facing seat identifier, e.g. "C4"
     * @param aisleGap render a gap after this seat
     */
    public record SeatView(Long id, int seatNumber, String label, SeatType seatType, boolean aisleGap) {
    }

    public static VenueLayoutResponse of(Venue venue, List<VenueRow> rows) {
        List<RowView> rowViews = rows.stream()
                .map(row -> new RowView(
                        row.getId(),
                        row.getRowIndex(),
                        row.getRowLabel(),
                        row.getSeatCount(),
                        row.getSeats().stream().map(VenueLayoutResponse::toSeatView).toList()))
                .toList();

        long seats = rowViews.stream().mapToLong(row -> row.seats().size()).sum();
        return new VenueLayoutResponse(venue.getId(), venue.getName(), seats, rowViews);
    }

    private static SeatView toSeatView(Seat seat) {
        return new SeatView(seat.getId(), seat.getSeatNumber(), seat.label(), seat.getSeatType(), seat.isAisleGap());
    }
}
