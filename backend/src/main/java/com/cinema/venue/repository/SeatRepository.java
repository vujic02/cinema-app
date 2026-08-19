package com.cinema.venue.repository;

import com.cinema.venue.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    /**
     * Every seat in a venue with its row eagerly joined, ordered as it should be drawn.
     * The join is what keeps {@link Seat#label()} from firing one query per seat — the
     * seat map is the hottest read in the app (TECH.md §3a, tier 2).
     */
    @Query("""
            select s from Seat s
              join fetch s.row r
            where r.venue.id = :venueId
            order by r.rowIndex asc, s.seatNumber asc
            """)
    List<Seat> findVenueSeats(@Param("venueId") Long venueId);

    long countByRowVenueId(Long venueId);

    /**
     * One seat, but only if it belongs to the given venue. Seat ids are global rather than
     * per-showing, so the seat-hold endpoints have to prove the seat is actually in the
     * auditorium the showing runs in; the fetch join returns the row alongside, which is what
     * {@link Seat#label()} needs to name the seat in an error message.
     */
    @Query("""
            select s from Seat s
              join fetch s.row r
            where s.id = :seatId and r.venue.id = :venueId
            """)
    Optional<Seat> findInVenue(@Param("seatId") Long seatId, @Param("venueId") Long venueId);

    /**
     * The same membership check for a whole checkout basket. Returns only the seats that really
     * are in the venue, so the caller compares sizes to find the ones that are not — one query
     * for the basket rather than one per seat.
     */
    @Query("""
            select s from Seat s
              join fetch s.row r
            where s.id in :seatIds and r.venue.id = :venueId
            """)
    List<Seat> findAllInVenue(@Param("seatIds") Collection<Long> seatIds, @Param("venueId") Long venueId);
}
