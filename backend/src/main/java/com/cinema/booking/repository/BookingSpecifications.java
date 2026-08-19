package com.cinema.booking.repository;

import com.cinema.booking.domain.Booking;
import com.cinema.booking.domain.BookingStatus;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * TECH.md §3a tier 3, the admin bookings screen: any combination of date range, venue, movie and
 * status. Same shape as {@link com.cinema.showing.repository.ShowingSpecifications} — each
 * factory returns {@code null} when its filter is absent and {@link #filter} drops those, so an
 * unfiltered call emits no WHERE clause rather than {@code 1=1}.
 */
public final class BookingSpecifications {

    private BookingSpecifications() {
    }

    /**
     * The date filters run on the <b>showing's</b> start time, not on when the booking was
     * placed. An admin asking for "this weekend" means the screenings, not the purchases; the
     * purchase timeline is what the analytics revenue-by-date series is for.
     * <p>
     * {@code join(..., JoinType.INNER)} rather than {@code root.get("showing").get(...)}: a path
     * expression through a to-one association emits its own join each time it is used, so
     * filtering on two showing fields would produce two joins. Reusing one keeps the query flat.
     */
    public static Specification<Booking> showingStartsAtOrAfter(Instant from) {
        return from == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(
                        root.join("showing", JoinType.INNER).get("startTime"), from);
    }

    /** Exclusive upper bound, so a whole day is [midnight, next midnight) with no overlap. */
    public static Specification<Booking> showingStartsBefore(Instant to) {
        return to == null ? null
                : (root, query, cb) -> cb.lessThan(
                        root.join("showing", JoinType.INNER).get("startTime"), to);
    }

    public static Specification<Booking> atVenue(Long venueId) {
        return venueId == null ? null
                : (root, query, cb) -> cb.equal(
                        root.join("showing", JoinType.INNER).get("venue").get("id"), venueId);
    }

    public static Specification<Booking> ofMovie(Long movieId) {
        return movieId == null ? null
                : (root, query, cb) -> cb.equal(
                        root.join("showing", JoinType.INNER).get("movie").get("id"), movieId);
    }

    public static Specification<Booking> hasStatus(BookingStatus status) {
        return status == null ? null
                : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** Reads the foreign key column directly — no join needed to compare an id. */
    public static Specification<Booking> forUser(Long userId) {
        return userId == null ? null
                : (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Booking> withReference(String reference) {
        return reference == null ? null
                : (root, query, cb) -> cb.equal(root.get("bookingReference"), reference);
    }

    @SafeVarargs
    public static Specification<Booking> filter(Specification<Booking>... specifications) {
        List<Specification<Booking>> present = Stream.of(specifications)
                .filter(Objects::nonNull)
                .toList();
        return Specification.allOf(present);
    }
}
