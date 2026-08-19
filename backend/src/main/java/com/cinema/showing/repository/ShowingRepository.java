package com.cinema.showing.repository;

import com.cinema.showing.domain.Showing;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * {@link JpaSpecificationExecutor} is here for the admin screens' dynamic filters
 * (date range × venue × movie), per TECH.md §3a tier 3. The Specifications themselves
 * land in Part 3; ordinary reads stay on derived queries.
 */
public interface ShowingRepository extends JpaRepository<Showing, Long>, JpaSpecificationExecutor<Showing> {

    List<Showing> findByStartTimeBetweenOrderByStartTimeAsc(Instant from, Instant to);

    boolean existsByVenueIdAndStartTime(Long venueId, Instant startTime);

    /**
     * Fetch-joins the two associations every showing response needs, so listing a day's
     * showings is one round trip instead of 1 + 2N.
     */
    @Query("""
            select s from Showing s
              join fetch s.movie
              join fetch s.venue
            where s.startTime >= :from and s.startTime < :to
            order by s.startTime asc
            """)
    List<Showing> findWithMovieAndVenueBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select s from Showing s
              join fetch s.movie
              join fetch s.venue
            where s.id = :id
            """)
    Optional<Showing> findWithMovieAndVenueById(@Param("id") Long id);

    /** Blocks deleting a venue that still has showings scheduled in it. */
    boolean existsByVenueId(Long venueId);

    /** Dashboard counter: how much is still to come (Part 5 analytics). */
    long countByStartTimeAfter(Instant cutoff);

    /** Blocks deleting a movie that is still scheduled somewhere. */
    boolean existsByMovieId(Long movieId);

    /** The {@code uq_showings_venue_start} check, tolerant of a showing keeping its own slot. */
    boolean existsByVenueIdAndStartTimeAndIdNot(Long venueId, Instant startTime, Long id);

    /**
     * Specification-driven reads still need {@code movie} and {@code venue} eagerly, and a
     * {@code join fetch} cannot be expressed in a Specification without breaking the count
     * query. An entity graph applies the same join and leaves the Specification clean.
     */
    @Override
    @EntityGraph(attributePaths = {"movie", "venue"})
    List<Showing> findAll(@Nullable Specification<Showing> spec, Sort sort);
}
