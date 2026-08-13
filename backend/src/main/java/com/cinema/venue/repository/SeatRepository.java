package com.cinema.venue.repository;

import com.cinema.venue.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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
}
