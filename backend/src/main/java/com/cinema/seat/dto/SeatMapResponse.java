package com.cinema.seat.dto;

import com.cinema.seat.domain.SeatStatus;
import com.cinema.venue.domain.SeatType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The seat picker's entire payload: the showing header the page needs anyway, plus every seat
 * in draw order with its status at this instant.
 *
 * @param holdTtlSeconds how long a hold placed from this screen will last, so the countdown in
 *                       the UI is driven by the server's setting rather than a hardcoded 300
 */
public record SeatMapResponse(
        Long showingId,
        String movieTitle,
        String venueName,
        Instant startTime,
        BigDecimal price,
        int holdTtlSeconds,
        int availableCount,
        List<RowView> rows
) {

    public record RowView(int rowIndex, String rowLabel, List<SeatView> seats) {
    }

    /**
     * @param label     customer-facing seat identifier, e.g. "C4"
     * @param aisleGap  render a gap after this seat
     * @param heldByYou this hold belongs to the caller — always false for an anonymous read.
     *                  The only user-specific field in the API, and it never appears on the
     *                  shared WebSocket topic.
     */
    public record SeatView(
            Long seatId,
            int seatNumber,
            String label,
            SeatType seatType,
            boolean aisleGap,
            SeatStatus status,
            boolean heldByYou
    ) {
    }
}
