package com.cinema.venue.service;

import com.cinema.booking.repository.BookingRepository;
import com.cinema.common.exception.ConflictException;
import com.cinema.common.exception.NotFoundException;
import com.cinema.showing.repository.ShowingRepository;
import com.cinema.venue.domain.Venue;
import com.cinema.venue.domain.VenueRow;
import com.cinema.venue.dto.VenueLayoutRequest;
import com.cinema.venue.dto.VenueLayoutResponse;
import com.cinema.venue.dto.VenueRequest;
import com.cinema.venue.dto.VenueResponse;
import com.cinema.venue.repository.VenueLayoutCounts;
import com.cinema.venue.repository.VenueRepository;
import com.cinema.venue.repository.VenueRowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VenueService {

    private final VenueRepository venues;
    private final VenueRowRepository venueRows;
    private final ShowingRepository showings;
    private final BookingRepository bookings;
    private final SeatLayoutGenerator layoutGenerator;

    @Transactional(readOnly = true)
    public List<VenueResponse> findAll() {
        Map<Long, VenueLayoutCounts> counts = venueRows.layoutCounts().stream()
                .collect(Collectors.toMap(VenueLayoutCounts::getVenueId, Function.identity()));

        return venues.findAllByOrderByNameAsc().stream()
                .map(venue -> {
                    // Absent means the venue has no layout yet, not that the query missed it.
                    VenueLayoutCounts count = counts.get(venue.getId());
                    return count == null
                            ? VenueResponse.of(venue, 0, 0)
                            : VenueResponse.of(venue, count.getRowCount(), count.getSeatCount());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public VenueResponse findById(Long id) {
        Venue venue = require(id);
        List<VenueRow> rows = venueRows.findByVenueIdOrderByRowIndexAsc(id);
        long seatCount = rows.stream().mapToLong(VenueRow::getSeatCount).sum();
        return VenueResponse.of(venue, rows.size(), seatCount);
    }

    @Transactional(readOnly = true)
    public VenueLayoutResponse findLayout(Long id) {
        Venue venue = require(id);
        return VenueLayoutResponse.of(venue, venueRows.findWithSeatsByVenueIdOrderByRowIndexAsc(id));
    }

    @Transactional
    public VenueResponse create(VenueRequest request) {
        String name = request.name().trim();
        if (venues.existsByNameIgnoreCase(name)) {
            throw new ConflictException("VENUE_NAME_TAKEN", "A venue named '" + name + "' already exists");
        }
        Venue venue = venues.save(Venue.builder()
                .name(name)
                .address(request.address().trim())
                .build());
        return VenueResponse.of(venue, 0, 0);
    }

    @Transactional
    public VenueResponse update(Long id, VenueRequest request) {
        Venue venue = require(id);
        String name = request.name().trim();
        if (venues.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("VENUE_NAME_TAKEN", "A venue named '" + name + "' already exists");
        }
        venue.setName(name);
        venue.setAddress(request.address().trim());
        return findByIdAfterSave(venues.save(venue));
    }

    @Transactional
    public void delete(Long id) {
        Venue venue = require(id);
        // showings.venue_id has no ON DELETE CASCADE, and deliberately so — losing a venue
        // should never silently take its schedule with it.
        if (showings.existsByVenueId(id)) {
            throw new ConflictException("VENUE_HAS_SHOWINGS",
                    "This venue still has showings scheduled. Delete those first.");
        }
        venues.delete(venue);
    }

    /**
     * Replaces a venue's entire seating plan. Not a merge: the old rows and seats are dropped
     * and rebuilt from the spec.
     */
    @Transactional
    public VenueLayoutResponse replaceLayout(Long id, VenueLayoutRequest request) {
        Venue venue = require(id);

        // Seats are referenced by bookings.seat_id. Rebuilding the layout under a sold ticket
        // would either fail on the foreign key or, worse, silently move someone's seat.
        if (bookings.existsByShowingVenueId(id)) {
            throw new ConflictException("LAYOUT_LOCKED",
                    "This venue has bookings against it, so its seat layout can no longer be changed");
        }

        // Two flushes on purpose. The new rows reuse the same (venue_id, row_index) and
        // (venue_id, row_label) values as the old ones, so if Hibernate ordered the inserts
        // before the deletes the unique constraints would fire.
        venue.getRows().clear();
        venues.saveAndFlush(venue);

        layoutGenerator.generate(venue, request);
        venues.saveAndFlush(venue);

        return VenueLayoutResponse.of(venue, venueRows.findWithSeatsByVenueIdOrderByRowIndexAsc(id));
    }

    private VenueResponse findByIdAfterSave(Venue venue) {
        List<VenueRow> rows = venueRows.findByVenueIdOrderByRowIndexAsc(venue.getId());
        return VenueResponse.of(venue, rows.size(), rows.stream().mapToLong(VenueRow::getSeatCount).sum());
    }

    private Venue require(Long id) {
        return venues.findById(id).orElseThrow(() -> new NotFoundException("Venue", id));
    }
}
