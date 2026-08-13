package com.cinema.venue.service;

import com.cinema.common.exception.BadRequestException;
import com.cinema.venue.domain.SeatType;
import com.cinema.venue.domain.Venue;
import com.cinema.venue.domain.VenueRow;
import com.cinema.venue.dto.VenueLayoutRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The generator has no repository and no transaction, so it is testable without a container —
 * which is the point of keeping it out of {@link VenueService}.
 */
class SeatLayoutGeneratorTest {

    private final SeatLayoutGenerator generator = new SeatLayoutGenerator();

    @Test
    @DisplayName("builds the TECH.md §6 reference layout: 4/6/6/6/7 across rows A-E")
    void buildsReferenceLayout() {
        Venue venue = Venue.builder().name("Reference").address("somewhere").build();

        generator.generate(venue, new VenueLayoutRequest(List.of(
                row(4), row(6), row(6), row(6), row(7))));

        assertThat(venue.getRows()).hasSize(5);
        assertThat(venue.getRows()).extracting(VenueRow::getRowLabel).containsExactly("A", "B", "C", "D", "E");
        assertThat(venue.getRows()).extracting(VenueRow::getRowIndex).containsExactly(1, 2, 3, 4, 5);
        assertThat(venue.getRows()).extracting(row -> row.getSeats().size()).containsExactly(4, 6, 6, 6, 7);
        assertThat(venue.getRows().stream().mapToInt(row -> row.getSeats().size()).sum()).isEqualTo(29);
    }

    @Test
    @DisplayName("seat labels combine the row label with the seat number")
    void buildsSeatLabels() {
        Venue venue = Venue.builder().name("Labels").address("somewhere").build();

        generator.generate(venue, new VenueLayoutRequest(List.of(row(3), row(3))));

        assertThat(venue.getRows().get(1).getSeats()).extracting(seat -> seat.label())
                .containsExactly("B1", "B2", "B3");
    }

    @Test
    @DisplayName("an aisle gap is flagged on the seat it follows, and nowhere else")
    void marksAisleGaps() {
        Venue venue = Venue.builder().name("Gaps").address("somewhere").build();

        generator.generate(venue, new VenueLayoutRequest(List.of(
                new VenueLayoutRequest.RowSpec(null, 6, Set.of(2, 4), null))));

        assertThat(venue.getRows().getFirst().getSeats()).extracting(seat -> seat.isAisleGap())
                .containsExactly(false, true, false, true, false, false);
    }

    @Test
    @DisplayName("an explicit label wins over the positional default and is upper-cased")
    void honoursExplicitLabels() {
        Venue venue = Venue.builder().name("Explicit").address("somewhere").build();

        generator.generate(venue, new VenueLayoutRequest(List.of(
                new VenueLayoutRequest.RowSpec("aa", 2, null, null),
                new VenueLayoutRequest.RowSpec(null, 2, null, null))));

        // The second row falls back to its position, which is index 1 -> "B".
        assertThat(venue.getRows()).extracting(VenueRow::getRowLabel).containsExactly("AA", "B");
    }

    @Test
    @DisplayName("seat type applies to the whole row and defaults to STANDARD")
    void appliesSeatType() {
        Venue venue = Venue.builder().name("Types").address("somewhere").build();

        generator.generate(venue, new VenueLayoutRequest(List.of(
                new VenueLayoutRequest.RowSpec(null, 2, null, SeatType.PREMIUM),
                row(2))));

        assertThat(venue.getRows().getFirst().getSeats()).allMatch(s -> s.getSeatType() == SeatType.PREMIUM);
        assertThat(venue.getRows().get(1).getSeats()).allMatch(s -> s.getSeatType() == SeatType.STANDARD);
    }

    @Test
    @DisplayName("an aisle gap at or past the end of the row is rejected")
    void rejectsOutOfRangeAisleGap() {
        Venue venue = Venue.builder().name("Bad gap").address("somewhere").build();

        assertThatThrownBy(() -> generator.generate(venue, new VenueLayoutRequest(List.of(
                new VenueLayoutRequest.RowSpec(null, 4, Set.of(4), null)))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("aisle gap after seat 4");
    }

    @Test
    @DisplayName("duplicate row labels are rejected before the unique constraint sees them")
    void rejectsDuplicateLabels() {
        Venue venue = Venue.builder().name("Dupes").address("somewhere").build();

        assertThatThrownBy(() -> generator.generate(venue, new VenueLayoutRequest(List.of(
                new VenueLayoutRequest.RowSpec("A", 2, null, null),
                new VenueLayoutRequest.RowSpec("a", 2, null, null)))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("used more than once");
    }

    @Test
    @DisplayName("default labels keep going past Z, so the scheme never runs out")
    void defaultLabelsWrapPastZ() {
        assertThat(SeatLayoutGenerator.defaultLabel(0)).isEqualTo("A");
        assertThat(SeatLayoutGenerator.defaultLabel(25)).isEqualTo("Z");
        assertThat(SeatLayoutGenerator.defaultLabel(26)).isEqualTo("AA");
        assertThat(SeatLayoutGenerator.defaultLabel(27)).isEqualTo("AB");
        assertThat(SeatLayoutGenerator.defaultLabel(51)).isEqualTo("AZ");
        assertThat(SeatLayoutGenerator.defaultLabel(52)).isEqualTo("BA");
    }

    private static VenueLayoutRequest.RowSpec row(int seatCount) {
        return new VenueLayoutRequest.RowSpec(null, seatCount, null, null);
    }
}
