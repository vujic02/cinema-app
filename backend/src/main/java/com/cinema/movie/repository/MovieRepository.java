package com.cinema.movie.repository;

import com.cinema.movie.domain.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    List<Movie> findAllByOrderByTitleAsc();

    List<Movie> findByTitleContainingIgnoreCase(String fragment);
}
