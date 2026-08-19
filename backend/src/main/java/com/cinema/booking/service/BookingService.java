package com.cinema.booking.service;

import com.cinema.auth.domain.Role;
import com.cinema.auth.domain.User;
import com.cinema.auth.repository.UserRepository;
import com.cinema.auth.security.UserPrincipal;
import com.cinema.booking.domain.Booking;
import com.cinema.booking.domain.BookingStatus;
import com.cinema.booking.dto.AnalyticsResponse;
import com.cinema.booking.dto.BookingRequest;
import com.cinema.booking.dto.BookingResponse;
import com.cinema.booking.dto.MyBookingsResponse;
import com.cinema.booking.repository.BookingRepository;
import com.cinema.booking.repository.BookingSpecifications;
import com.cinema.common.exception.BadRequestException;
import com.cinema.common.exception.ConflictException;
import com.cinema.common.exception.NotFoundException;
import com.cinema.seat.domain.HoldKey;
import com.cinema.seat.domain.SeatStatus;
import com.cinema.seat.event.SeatEventPublisher;
import com.cinema.seat.service.SeatHoldStore;
import com.cinema.showing.domain.Showing;
import com.cinema.showing.repository.ShowingRepository;
import com.cinema.venue.domain.Seat;
import com.cinema.venue.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Checkout and booking history — TECH.md §5 step 5, plus the admin reporting on top of it.
 */
@Service
@RequiredArgsConstructor
public class BookingService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final BookingRepository bookings;
    private final ShowingRepository showings;
    private final SeatRepository seats;
    private final UserRepository users;
    private final SeatHoldStore holds;
    private final SeatEventPublisher events;
    private final BookingReferenceGenerator references;

    /**
     * Turns held seats into sold ones.
     *
     * <p>The ordering here is the whole point. Validation and the insert happen inside one
     * transaction; releasing the Redis holds and telling the room happens
     * <b>after that transaction commits</b>. Doing either one early would mean announcing a sale
     * that a rollback then un-did — the room would show seats as permanently sold that nobody
     * owns, and only a server restart would clear it.
     *
     * <p>If the commit fails, the holds are left alone and simply expire on their TTL, which is
     * the same state the customer was in before they pressed the button.
     */
    @Transactional
    public BookingResponse confirm(BookingRequest request, Long userId) {
        Showing showing = requireOpenShowing(request.showingId());
        List<Long> seatIds = distinct(request.seatIds());

        Map<Long, Seat> basket = requireSeatsOf(showing, seatIds);
        rejectAlreadySold(showing.getId(), seatIds, basket);
        rejectSeatsNotHeldBy(showing.getId(), seatIds, basket, userId);

        User buyer = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User", userId));

        String reference = references.next();
        // Price is snapshotted per row: editing showings.price tomorrow must not rewrite what
        // this customer actually paid (TECH.md §6).
        BigDecimal price = showing.getPrice();

        List<Booking> rows = seatIds.stream()
                .map(seatId -> Booking.builder()
                        .bookingReference(reference)
                        .showing(showing)
                        .seat(basket.get(seatId))
                        .user(buyer)
                        .status(BookingStatus.SOLD)
                        .pricePaid(price)
                        .build())
                .toList();

        // saveAll, so uq_bookings_showing_seat is evaluated for the whole basket in one flush.
        // If Redis was unavailable and two checkouts raced past the hold check, this is the
        // backstop: one of them fails and GlobalExceptionHandler turns it into a 409.
        bookings.saveAll(rows);

        publishSoldAfterCommit(showing.getId(), seatIds);

        return BookingResponse.of(rows, Instant.now(), false);
    }

    @Transactional(readOnly = true)
    public MyBookingsResponse findMine(Long userId) {
        Instant now = Instant.now();
        List<BookingResponse> all = groupByReference(bookings.findDetailedByUserId(userId), now, false);

        return new MyBookingsResponse(
                all.stream().filter(BookingResponse::upcoming).toList(),
                all.stream().filter(booking -> !booking.upcoming()).toList());
    }

    /**
     * One purchase by its printed reference.
     *
     * <p>A reference that exists but belongs to somebody else answers <b>404, not 403</b>. A 403
     * would confirm the reference is real, which turns this endpoint into an oracle for guessing
     * other people's booking codes. Admins can read any of them.
     */
    @Transactional(readOnly = true)
    public BookingResponse findByReference(String reference, UserPrincipal caller) {
        List<Booking> rows = bookings.findDetailedByReference(reference);
        boolean admin = caller.role() == Role.ADMIN;

        if (rows.isEmpty() || (!admin && !rows.get(0).getUser().getId().equals(caller.userId()))) {
            throw new NotFoundException("Booking " + reference + " not found");
        }
        return BookingResponse.of(rows, Instant.now(), admin);
    }

    /** The admin bookings table: every filter optional, every combination allowed. */
    @Transactional(readOnly = true)
    public List<BookingResponse> findForAdmin(LocalDate from,
                                              LocalDate to,
                                              Long venueId,
                                              Long movieId,
                                              BookingStatus status) {
        List<Booking> rows = bookings.findAll(
                BookingSpecifications.filter(
                        BookingSpecifications.showingStartsAtOrAfter(
                                from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant()),
                        // Inclusive end date: "to 5 March" means all of 5 March.
                        BookingSpecifications.showingStartsBefore(
                                to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()),
                        BookingSpecifications.atVenue(venueId),
                        BookingSpecifications.ofMovie(movieId),
                        BookingSpecifications.hasStatus(status)),
                NEWEST_FIRST);

        return groupByReference(rows, Instant.now(), true);
    }

    /**
     * @param since optional cutoff for both series; absent means all time
     */
    @Transactional(readOnly = true)
    public AnalyticsResponse analytics(LocalDate since) {
        Instant from = since == null ? null : since.atStartOfDay(ZoneOffset.UTC).toInstant();

        AnalyticsResponse.Totals totals = new AnalyticsResponse.Totals(
                bookings.countDistinctPurchases(),
                bookings.countByStatus(BookingStatus.SOLD),
                bookings.totalRevenue(),
                showings.countByStartTimeAfter(Instant.now()));

        return AnalyticsResponse.of(totals, bookings.bookingsPerShowing(from), bookings.revenueByDate(from));
    }

    // ------------------------------------------------------------------ checkout validation

    /**
     * Every seat must still be held by <em>this</em> caller. A hold that lapsed while the
     * customer was on the checkout screen is the expected failure here, and it is worth its own
     * code: the UI sends them back to the seat map rather than showing a generic conflict.
     */
    private void rejectSeatsNotHeldBy(Long showingId, List<Long> seatIds, Map<Long, Seat> basket, Long userId) {
        List<String> expired = new ArrayList<>();
        List<String> stolen = new ArrayList<>();

        for (Long seatId : seatIds) {
            Optional<Long> holder = holds.holderOf(new HoldKey(showingId, seatId));
            if (holder.isEmpty()) {
                expired.add(basket.get(seatId).label());
            } else if (!holder.get().equals(userId)) {
                stolen.add(basket.get(seatId).label());
            }
        }

        if (!stolen.isEmpty()) {
            throw new ConflictException("SEAT_HELD",
                    "Someone else is holding " + String.join(", ", stolen) + ". Pick another seat.");
        }
        if (!expired.isEmpty()) {
            throw new ConflictException("HOLD_EXPIRED",
                    "Your hold on " + String.join(", ", expired) + " expired. Please select your seats again.");
        }
    }

    /**
     * Confirming the same basket twice lands here on the second attempt: the sale already exists,
     * and its hold key was deleted by the first one.
     */
    private void rejectAlreadySold(Long showingId, List<Long> seatIds, Map<Long, Seat> basket) {
        List<Long> sold = bookings.findSoldSeatIdsAmong(showingId, seatIds);
        if (!sold.isEmpty()) {
            String labels = sold.stream().map(id -> basket.get(id).label()).sorted().toList().toString();
            throw new ConflictException("SEAT_SOLD", "Already sold: " + labels);
        }
    }

    /**
     * Seat ids are global rather than per-showing, so a caller can pair a perfectly valid seat id
     * with a showing in a different auditorium. One query settles membership for the whole basket
     * and yields the rows needed for the labels.
     */
    private Map<Long, Seat> requireSeatsOf(Showing showing, List<Long> seatIds) {
        Map<Long, Seat> found = new LinkedHashMap<>();
        seats.findAllInVenue(seatIds, showing.getVenue().getId())
                .forEach(seat -> found.put(seat.getId(), seat));

        if (found.size() != seatIds.size()) {
            List<Long> strangers = seatIds.stream().filter(id -> !found.containsKey(id)).toList();
            throw new BadRequestException("SEAT_NOT_IN_SHOWING",
                    "Seats " + strangers + " are not part of " + showing.getVenue().getName());
        }
        return found;
    }

    private Showing requireOpenShowing(Long showingId) {
        Showing showing = showings.findWithMovieAndVenueById(showingId)
                .orElseThrow(() -> new NotFoundException("Showing", showingId));

        if (!showing.getStartTime().isAfter(Instant.now())) {
            throw new ConflictException("SHOWING_STARTED",
                    "This showing has already started, so tickets can no longer be bought for it");
        }
        return showing;
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Deletes the holds and broadcasts {@code SOLD} once the sale is durable.
     *
     * <p>{@code forceRelease} rather than the compare-and-delete used elsewhere: the hold being
     * cleared has just been proven to belong to this buyer, and the sale supersedes it either
     * way. Deleting the key emits no Redis expiry event, so the only broadcast the room sees for
     * these seats is this one.
     */
    private void publishSoldAfterCommit(Long showingId, List<Long> seatIds) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (Long seatId : seatIds) {
                    holds.forceRelease(new HoldKey(showingId, seatId));
                    events.publish(showingId, seatId, SeatStatus.SOLD);
                }
            }
        });
    }

    /**
     * Rows back into purchases, preserving the order the query returned them in — a
     * LinkedHashMap keyed on the reference, so the sort applied by the database survives.
     */
    private List<BookingResponse> groupByReference(List<Booking> rows, Instant now, boolean includeCustomer) {
        Map<String, List<Booking>> byReference = new LinkedHashMap<>();
        rows.forEach(row -> byReference
                .computeIfAbsent(row.getBookingReference(), key -> new ArrayList<>())
                .add(row));

        return byReference.values().stream()
                .map(group -> BookingResponse.of(group, now, includeCustomer))
                .toList();
    }

    /** Collapses a double-submitted seat instead of rejecting the basket outright. */
    private List<Long> distinct(List<Long> seatIds) {
        return seatIds.stream().distinct().toList();
    }
}
