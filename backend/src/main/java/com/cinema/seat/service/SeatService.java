package com.cinema.seat.service;

import com.cinema.booking.repository.BookingRepository;
import com.cinema.common.exception.BadRequestException;
import com.cinema.common.exception.ConflictException;
import com.cinema.common.exception.NotFoundException;
import com.cinema.config.AppProperties;
import com.cinema.seat.domain.HoldKey;
import com.cinema.seat.domain.SeatStatus;
import com.cinema.seat.dto.SeatHoldResponse;
import com.cinema.seat.dto.SeatMapResponse;
import com.cinema.seat.event.SeatEventPublisher;
import com.cinema.seat.repository.SeatMapEntry;
import com.cinema.seat.repository.SeatMapRepository;
import com.cinema.showing.domain.Showing;
import com.cinema.showing.repository.ShowingRepository;
import com.cinema.venue.domain.Seat;
import com.cinema.venue.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The flagship feature of TECH.md §5: reading a showing's seat map, and holding a seat against
 * everyone else who is looking at the same screen.
 *
 * <p>Deliberately <b>not</b> {@code @Transactional}. Every read here is a single query that
 * returns DTOs or an eagerly-fetched entity, so there is nothing to lazy-load and nothing to roll
 * back — and wrapping the methods would mean a database connection sat idle for the duration of
 * a Redis round trip on the busiest endpoints in the app.
 */
@Service
@RequiredArgsConstructor
public class SeatService {

    private final ShowingRepository showings;
    private final SeatRepository seats;
    private final SeatMapRepository seatMaps;
    private final BookingRepository bookings;
    private final SeatHoldStore holds;
    private final SeatEventPublisher events;
    private final AppProperties appProperties;

    /**
     * Status is assembled from the two stores that actually own it — MySQL for sales, Redis for
     * live holds — rather than from any accumulated broadcast state, so a client that missed a
     * WebSocket frame is made whole by a refresh.
     *
     * @param viewerId the authenticated caller, or {@code null}. Anonymous browsing is allowed
     *                 (the catalogue is public); it simply never sees {@code heldByYou}.
     */
    public SeatMapResponse seatMap(Long showingId, Long viewerId) {
        Showing showing = requireShowing(showingId);

        Map<Long, Long> heldBy = holds.holdsForShowing(showingId);
        List<SeatMapEntry> entries = seatMaps.findSeatMap(showingId, showing.getVenue().getId());

        Map<RowKey, List<SeatMapResponse.SeatView>> byRow = new LinkedHashMap<>();
        int available = 0;

        for (SeatMapEntry entry : entries) {
            Long holder = heldBy.get(entry.seatId());
            SeatStatus status = entry.sold()
                    ? SeatStatus.SOLD
                    : holder == null ? SeatStatus.AVAILABLE : SeatStatus.HELD;
            if (status == SeatStatus.AVAILABLE) {
                available++;
            }

            byRow.computeIfAbsent(new RowKey(entry.rowIndex(), entry.rowLabel()), key -> new ArrayList<>())
                    .add(new SeatMapResponse.SeatView(
                            entry.seatId(),
                            entry.seatNumber(),
                            entry.label(),
                            entry.seatType(),
                            entry.aisleGap(),
                            status,
                            viewerId != null && viewerId.equals(holder)));
        }

        // The query already returns rows in draw order, and LinkedHashMap keeps it.
        List<SeatMapResponse.RowView> rows = byRow.entrySet().stream()
                .map(row -> new SeatMapResponse.RowView(row.getKey().index(), row.getKey().label(), row.getValue()))
                .toList();

        return new SeatMapResponse(
                showing.getId(),
                showing.getMovie().getTitle(),
                showing.getVenue().getName(),
                showing.getStartTime(),
                showing.getPrice(),
                appProperties.seatHold().ttlSeconds(),
                available,
                rows);
    }

    /** TECH.md §5 step 2: reserve the seat for this user, or tell them who was faster. */
    public SeatHoldResponse hold(Long showingId, Long seatId, Long userId) {
        Showing showing = requireOpenShowing(showingId);
        Seat seat = requireSeatOf(showing, seatId);

        // Checked before Redis so a sold seat never acquires a hold key at all. The window
        // between this and the sale is covered further down: checkout deletes the hold it is
        // buying against, and uq_bookings_showing_seat is the final word either way.
        if (bookings.existsByShowingIdAndSeatId(showingId, seatId)) {
            throw new ConflictException("SEAT_SOLD", "Seat " + seat.label() + " has already been sold");
        }

        HoldKey key = new HoldKey(showingId, seatId);
        HoldResult result = holds.acquire(key, userId, appProperties.seatHold().ttl());

        if (result.outcome() == HoldResult.Outcome.HELD_BY_OTHER) {
            throw new ConflictException("SEAT_HELD",
                    "Seat " + seat.label() + " is being held by someone else. Pick another seat.");
        }
        // Only a genuine acquisition changes what the room sees. Re-clicking a seat you already
        // hold broadcasts nothing, because nothing changed.
        if (result.outcome() == HoldResult.Outcome.ACQUIRED) {
            events.publish(showingId, seatId, SeatStatus.HELD);
        }

        Duration remaining = result.remaining();
        return new SeatHoldResponse(showingId, seatId, seat.label(), SeatStatus.HELD,
                remaining.toSeconds(), Instant.now().plus(remaining));
    }

    /**
     * Giving a seat back early — deselecting it, or navigating away from the picker.
     *
     * <p>Idempotent on purpose: a hold that already lapsed is not an error to release. The
     * frontend releases on unmount, which routinely happens after the TTL has fired, and the
     * expiry listener has already told the room about that seat.
     */
    public void release(Long showingId, Long seatId, Long userId) {
        ReleaseOutcome outcome = holds.release(new HoldKey(showingId, seatId), userId);

        switch (outcome) {
            case RELEASED -> events.publish(showingId, seatId, SeatStatus.AVAILABLE);
            case NOT_YOURS -> throw new ConflictException("HOLD_NOT_YOURS",
                    "That seat is held by someone else, so it is not yours to release");
            case NOT_HELD -> { /* Already free. Nothing changed, so nothing to broadcast. */ }
        }
    }

    private Showing requireShowing(Long showingId) {
        return showings.findWithMovieAndVenueById(showingId)
                .orElseThrow(() -> new NotFoundException("Showing", showingId));
    }

    private Showing requireOpenShowing(Long showingId) {
        Showing showing = requireShowing(showingId);
        if (!showing.getStartTime().isAfter(Instant.now())) {
            throw new ConflictException("SHOWING_STARTED",
                    "This showing has already started, so its seats can no longer be held");
        }
        return showing;
    }

    /**
     * Seat ids are global, not per-showing, so nothing stops a caller pairing a valid seat id
     * with a showing in a different auditorium. The fetch join settles membership and yields the
     * label in the same query.
     */
    private Seat requireSeatOf(Showing showing, Long seatId) {
        return seats.findInVenue(seatId, showing.getVenue().getId())
                .orElseThrow(() -> new BadRequestException("SEAT_NOT_IN_SHOWING",
                        "Seat " + seatId + " is not part of " + showing.getVenue().getName()));
    }

    /** Groups seats into their row without a second pass over the entries. */
    private record RowKey(int index, String label) {
    }
}
