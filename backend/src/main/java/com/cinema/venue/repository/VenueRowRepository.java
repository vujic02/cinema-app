package com.cinema.venue.repository;

import com.cinema.venue.domain.VenueRow;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface VenueRowRepository extends JpaRepository<VenueRow, Long> {

    List<VenueRow> findByVenueIdOrderByRowIndexAsc(Long venueId);

    /**
     * Same read, with the seats attached — one query for a whole seating plan instead of one
     * per row. Safe as a single-collection entity graph; fetching {@code rows} and
     * {@code rows.seats} together from {@link com.cinema.venue.domain.Venue} would be two bags
     * at once and Hibernate rejects that.
     */
    @EntityGraph(attributePaths = "seats")
    List<VenueRow> findWithSeatsByVenueIdOrderByRowIndexAsc(Long venueId);

    void deleteByVenueId(Long venueId);

    /** Row and seat totals for every venue that has a layout, in one query. */
    @Query("""
            select r.venue.id as venueId,
                   count(distinct r.id) as rowCount,
                   count(s.id) as seatCount
            from VenueRow r
              left join r.seats s
            group by r.venue.id
            """)
    List<VenueLayoutCounts> layoutCounts();
}
