package com.cinema.venue.repository;

import com.cinema.venue.domain.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VenueRepository extends JpaRepository<Venue, Long> {

    Optional<Venue> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    /** Same uniqueness check as above, but tolerant of the venue being renamed to its own name. */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<Venue> findAllByOrderByNameAsc();
}
