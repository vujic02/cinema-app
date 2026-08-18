package com.cinema.seat.repository;

import com.cinema.venue.domain.Seat;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * The hottest read in the application (TECH.md §3a tier 2), so it gets its own query rather than
 * borrowing one.
 * <p>
 * A marker {@link Repository} rather than a {@code JpaRepository}: nothing here writes seats, and
 * the only method is the projection below.
 */
public interface SeatMapRepository extends Repository<Seat, Long> {

    /**
     * Every seat of a venue with its sold-or-not status for one showing, in draw order, in a
     * single round trip.
     * <p>
     * The {@code left join Booking … on} is an entity join: {@code Seat} has no association to
     * {@code Booking}, and adding one would drag a collection of every ticket ever sold onto the
     * seat. Joining on the condition instead keeps it to one row per seat, and the {@code left}
     * is what makes an unsold seat come back with a null booking id rather than vanish.
     * <p>
     * Holds are <em>not</em> in here — they live in Redis with a TTL and never touch MySQL.
     */
    @Query("""
            select new com.cinema.seat.repository.SeatMapEntry(
                r.rowIndex, r.rowLabel, s.id, s.seatNumber, s.seatType, s.aisleGap, b.id)
            from Seat s
              join s.row r
              left join Booking b
                on b.seat = s
                and b.showing.id = :showingId
                and b.status = com.cinema.booking.domain.BookingStatus.SOLD
            where r.venue.id = :venueId
            order by r.rowIndex asc, s.seatNumber asc
            """)
    List<SeatMapEntry> findSeatMap(@Param("showingId") Long showingId, @Param("venueId") Long venueId);
}
