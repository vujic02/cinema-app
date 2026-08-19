package com.cinema.booking.repository;

import com.cinema.booking.domain.Booking;
import com.cinema.booking.domain.BookingStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * {@link JpaSpecificationExecutor} backs the admin bookings screen's dynamic date-range / venue /
 * status filters (TECH.md §3a tier 3); the Specifications live in {@link BookingSpecifications}.
 */
public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Booking> findByShowingIdAndStatus(Long showingId, BookingStatus status);

    List<Booking> findByBookingReference(String bookingReference);

    boolean existsByShowingIdAndSeatId(Long showingId, Long seatId);

    boolean existsByBookingReference(String bookingReference);

    /** Guard for deleting a showing (Part 3): a showing with tickets against it cannot vanish. */
    boolean existsByShowingId(Long showingId);

    /**
     * Guard for regenerating a venue's seat layout: replacing the layout deletes and recreates
     * every seat row, which would orphan the bookings that reference those seats.
     */
    boolean existsByShowingVenueId(Long venueId);

    /** Seat ids already sold for a showing — the "unavailable" half of the seat map. */
    @Query("""
            select b.seat.id from Booking b
            where b.showing.id = :showingId and b.status = com.cinema.booking.domain.BookingStatus.SOLD
            """)
    List<Long> findSoldSeatIds(@Param("showingId") Long showingId);

    /**
     * The subset of {@code seatIds} already sold for this showing. One query for a whole
     * checkout basket rather than an exists-check per seat, and it names the seats it rejects.
     */
    @Query("""
            select b.seat.id from Booking b
            where b.showing.id = :showingId
              and b.seat.id in :seatIds
              and b.status = com.cinema.booking.domain.BookingStatus.SOLD
            """)
    List<Long> findSoldSeatIdsAmong(@Param("showingId") Long showingId,
                                    @Param("seatIds") Collection<Long> seatIds);

    /**
     * Everything {@link com.cinema.booking.dto.BookingResponse} reads, in one round trip.
     * <p>
     * Five fetch joins because that record touches the showing, its movie and venue, and each
     * seat's row for the "C4" label — all of which are lazy. Without them, rendering one
     * customer's booking list is a query per seat plus a query per booking.
     */
    @Query("""
            select b from Booking b
              join fetch b.showing s
              join fetch s.movie
              join fetch s.venue
              join fetch b.seat seat
              join fetch seat.row
            where b.user.id = :userId
            order by s.startTime desc, b.id asc
            """)
    List<Booking> findDetailedByUserId(@Param("userId") Long userId);

    /** The same shape, for one purchase. Used by the reference lookup and the checkout response. */
    @Query("""
            select b from Booking b
              join fetch b.showing s
              join fetch s.movie
              join fetch s.venue
              join fetch b.seat seat
              join fetch seat.row
              join fetch b.user
            where b.bookingReference = :reference
            order by b.id asc
            """)
    List<Booking> findDetailedByReference(@Param("reference") String reference);

    /**
     * Specification-driven admin reads still need the same associations, and a {@code join fetch}
     * cannot be expressed in a Specification without breaking the count query. An entity graph
     * applies the same joins and leaves the Specification clean — the identical trick
     * {@code ShowingRepository} uses.
     */
    @Override
    @EntityGraph(attributePaths = {"showing", "showing.movie", "showing.venue", "seat", "seat.row", "user"})
    List<Booking> findAll(@Nullable Specification<Booking> spec, Sort sort);

    // ------------------------------------------------------------------ analytics (tier 4)

    long countByStatus(BookingStatus status);

    @Query("""
            select coalesce(sum(b.pricePaid), 0) from Booking b
            where b.status = com.cinema.booking.domain.BookingStatus.SOLD
            """)
    java.math.BigDecimal totalRevenue();

    /** Distinct purchases rather than tickets — three seats on one reference is one sale. */
    @Query("""
            select count(distinct b.bookingReference) from Booking b
            where b.status = com.cinema.booking.domain.BookingStatus.SOLD
            """)
    long countDistinctPurchases();

    @Query("""
            select s.id           as showingId,
                   m.title        as movieTitle,
                   v.name         as venueName,
                   s.startTime    as startTime,
                   count(b)       as seatsSold,
                   sum(b.pricePaid) as revenue
            from Booking b
              join b.showing s
              join s.movie m
              join s.venue v
            where b.status = com.cinema.booking.domain.BookingStatus.SOLD
              and (:from is null or s.startTime >= :from)
            group by s.id, m.title, v.name, s.startTime
            order by sum(b.pricePaid) desc
            """)
    List<BookingsPerShowingView> bookingsPerShowing(@Param("from") Instant from);

    /**
     * {@code cast(... as Date)} is how JPQL truncates a timestamp to a day. It maps to MySQL's
     * DATE() and lets the projection expose a {@link java.time.LocalDate} rather than the raw
     * instant, which is what a daily series needs.
     */
    @Query("""
            select cast(b.createdAt as Date) as date,
                   count(b)                  as ticketsSold,
                   sum(b.pricePaid)          as revenue
            from Booking b
            where b.status = com.cinema.booking.domain.BookingStatus.SOLD
              and (:from is null or b.createdAt >= :from)
            group by cast(b.createdAt as Date)
            order by cast(b.createdAt as Date) desc
            """)
    List<RevenueByDateView> revenueByDate(@Param("from") Instant from);
}
