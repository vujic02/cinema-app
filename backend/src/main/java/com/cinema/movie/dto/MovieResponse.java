package com.cinema.movie.dto;

import com.cinema.movie.domain.Movie;

public record MovieResponse(
        Long id,
        String title,
        String description,
        int durationMinutes,
        String genre,
        String rating,
        int posterHue,
        String posterUrl
) {

    public static MovieResponse from(Movie movie) {
        return new MovieResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getDescription(),
                movie.getDurationMinutes(),
                movie.getGenre(),
                movie.getRating(),
                movie.getPosterHue(),
                movie.getPosterUrl());
    }
}
