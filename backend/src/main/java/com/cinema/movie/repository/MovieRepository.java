package com.cinema.movie.repository;

import com.cinema.movie.domain.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    List<Movie> findAllByOrderByTitleAsc();

    List<Movie> findByTitleContainingIgnoreCase(String fragment);

    /** What makes the TMDB import idempotent: a movie already seen is updated, not duplicated. */
    Optional<Movie> findByTmdbId(Long tmdbId);
}
