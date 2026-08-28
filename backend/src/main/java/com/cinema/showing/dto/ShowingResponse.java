package com.cinema.showing.dto;

import com.cinema.showing.domain.Showing;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Carries the movie fields the showings list renders (title, rating, artwork) so the UI does
 * not have to fetch each movie separately.
 */
public record ShowingResponse(
        Long id,
        Instant startTime,
        BigDecimal price,
        MovieSummary movie,
        VenueSummary venue
) {

    public record MovieSummary(Long id, String title, int durationMinutes, String genre, String rating,
                               int posterHue, String posterUrl) {
    }

    public record VenueSummary(Long id, String name) {
    }

    /** Requires {@code movie} and {@code venue} to be initialised — every query here fetches them. */
    public static ShowingResponse from(Showing showing) {
        return new ShowingResponse(
                showing.getId(),
                showing.getStartTime(),
                showing.getPrice(),
                new MovieSummary(
                        showing.getMovie().getId(),
                        showing.getMovie().getTitle(),
                        showing.getMovie().getDurationMinutes(),
                        showing.getMovie().getGenre(),
                        showing.getMovie().getRating(),
                        showing.getMovie().getPosterHue(),
                        showing.getMovie().getPosterUrl()),
                new VenueSummary(showing.getVenue().getId(), showing.getVenue().getName()));
    }
}
