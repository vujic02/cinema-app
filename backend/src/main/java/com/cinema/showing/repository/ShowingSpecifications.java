package com.cinema.showing.repository;

import com.cinema.showing.domain.Showing;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * TECH.md §3a tier 3. The admin schedule screen filters by any combination of date range, venue
 * and movie, which is exactly the case derived query methods handle badly — eight optional
 * combinations would need eight method names.
 * <p>
 * Every factory returns {@code null} for an absent filter, and {@link #filter} drops those, so
 * an unfiltered call produces a query with no WHERE clause at all rather than {@code 1=1}.
 */
public final class ShowingSpecifications {

    private ShowingSpecifications() {
    }

    /** Inclusive lower bound. */
    public static Specification<Showing> startsAtOrAfter(Instant from) {
        return from == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startTime"), from);
    }

    /** Exclusive upper bound, so a whole day is [midnight, next midnight) with no overlap. */
    public static Specification<Showing> startsBefore(Instant to) {
        return to == null ? null
                : (root, query, cb) -> cb.lessThan(root.get("startTime"), to);
    }

    /** Reads the foreign key column directly — no join needed to compare an id. */
    public static Specification<Showing> atVenue(Long venueId) {
        return venueId == null ? null
                : (root, query, cb) -> cb.equal(root.get("venue").get("id"), venueId);
    }

    public static Specification<Showing> ofMovie(Long movieId) {
        return movieId == null ? null
                : (root, query, cb) -> cb.equal(root.get("movie").get("id"), movieId);
    }

    @SafeVarargs
    public static Specification<Showing> filter(Specification<Showing>... specifications) {
        List<Specification<Showing>> present = Stream.of(specifications)
                .filter(Objects::nonNull)
                .toList();
        return Specification.allOf(present);
    }
}
