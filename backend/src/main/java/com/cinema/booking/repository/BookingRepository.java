package com.cinema.booking.repository;

import com.cinema.booking.domain.Booking;
import com.cinema.booking.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * {@link JpaSpecificationExecutor} backs the admin bookings screen's dynamic
 * date-range / venue / status filters (TECH.md §3a tier 3); the Specifications
 * themselves arrive in Part 5.
 */
public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Booking> findByShowingIdAndStatus(Long showingId, BookingStatus status);

    List<Booking> findByBookingReference(String bookingReference);

    boolean existsByShowingIdAndSeatId(Long showingId, Long seatId);

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
}
