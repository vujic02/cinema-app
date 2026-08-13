package com.cinema.venue.dto;

import com.cinema.venue.domain.SeatType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

/**
 * The row-spec an admin draws a venue's seating from. The TECH.md §6 reference layout is
 * {@code [{seatCount:4}, {seatCount:6}, {seatCount:6}, {seatCount:6}, {seatCount:7}]}.
 *
 * @param rows one entry per row, ordered screen-nearest first
 */
public record VenueLayoutRequest(

        @NotEmpty
        @Size(max = 50, message = "A venue cannot have more than 50 rows")
        @Valid
        List<RowSpec> rows
) {

    /**
     * @param label          optional display label; defaults to A, B, C … by position
     * @param seatCount      seats in this row, bounded by {@code ck_venue_rows_seat_count}
     * @param aisleGapsAfter seat numbers to render a gap after — {@code [3]} on a 6-seat row
     *                       splits it 3 + 3. Must be inside the row, and never the last seat,
     *                       which would only produce a trailing gap.
     * @param seatType       applied to every seat in the row; defaults to STANDARD
     */
    public record RowSpec(

            @Size(max = 4)
            String label,

            @Min(1)
            @Max(40)
            int seatCount,

            Set<@Min(1) Integer> aisleGapsAfter,

            SeatType seatType
    ) {
    }
}
