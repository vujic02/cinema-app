package com.cinema.venue.service;

import com.cinema.common.exception.BadRequestException;
import com.cinema.venue.domain.Seat;
import com.cinema.venue.domain.SeatType;
import com.cinema.venue.domain.Venue;
import com.cinema.venue.domain.VenueRow;
import com.cinema.venue.dto.VenueLayoutRequest;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns a row-spec into the {@code venue_rows} + {@code seats} graph.
 * <p>
 * Kept separate from {@link VenueService} because it is pure: no repositories, no transaction,
 * nothing to mock. The rules it enforces are the ones the database constraints cannot express —
 * the DB can say "seat_count between 1 and 40" and "row labels are unique per venue", but not
 * "an aisle gap must fall inside its row".
 */
@Component
public class SeatLayoutGenerator {

    /**
     * Builds and attaches rows to {@code venue}. The caller owns persistence; this only mutates
     * the object graph.
     */
    public void generate(Venue venue, VenueLayoutRequest request) {
        Set<String> usedLabels = new HashSet<>();
        List<VenueLayoutRequest.RowSpec> specs = request.rows();

        for (int index = 0; index < specs.size(); index++) {
            VenueLayoutRequest.RowSpec spec = specs.get(index);
            String label = resolveLabel(spec.label(), index);

            if (!usedLabels.add(label)) {
                throw new BadRequestException("DUPLICATE_ROW_LABEL",
                        "Row label '" + label + "' is used more than once");
            }

            venue.addRow(buildRow(spec, index, label));
        }
    }

    private VenueRow buildRow(VenueLayoutRequest.RowSpec spec, int index, String label) {
        Set<Integer> gaps = spec.aisleGapsAfter() == null ? Set.of() : spec.aisleGapsAfter();
        validateGaps(gaps, spec.seatCount(), label);

        VenueRow row = VenueRow.builder()
                // 1-based, screen-nearest first, matching venue_rows.row_index.
                .rowIndex(index + 1)
                .rowLabel(label)
                .seatCount(spec.seatCount())
                .build();

        SeatType seatType = spec.seatType() == null ? SeatType.STANDARD : spec.seatType();
        for (int seatNumber = 1; seatNumber <= spec.seatCount(); seatNumber++) {
            row.addSeat(Seat.builder()
                    .seatNumber(seatNumber)
                    .seatType(seatType)
                    .aisleGap(gaps.contains(seatNumber))
                    .build());
        }
        return row;
    }

    private void validateGaps(Set<Integer> gaps, int seatCount, String label) {
        for (Integer gap : gaps) {
            // A gap after the final seat would render a trailing space and nothing else, which
            // is always a mistake in the spec rather than an intentional layout.
            if (gap >= seatCount) {
                throw new BadRequestException("INVALID_AISLE_GAP",
                        "Row " + label + " has " + seatCount + " seats, so an aisle gap after seat "
                                + gap + " is not possible");
            }
        }
    }

    private String resolveLabel(String supplied, int index) {
        if (supplied != null && !supplied.isBlank()) {
            return supplied.trim().toUpperCase(Locale.ROOT);
        }
        return defaultLabel(index);
    }

    /**
     * Spreadsheet-column labelling: A…Z, then AA, AB, … so the scheme never runs out and always
     * fits {@code venue_rows.row_label}'s 4 characters.
     */
    static String defaultLabel(int index) {
        StringBuilder label = new StringBuilder();
        int remaining = index;
        do {
            label.insert(0, (char) ('A' + remaining % 26));
            remaining = remaining / 26 - 1;
        } while (remaining >= 0);
        return label.toString();
    }
}
