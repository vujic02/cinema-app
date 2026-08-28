package com.cinema.catalog.dto;

import java.util.List;

/**
 * What one run of the TMDB import actually did.
 *
 * <p>Split into created/updated rather than a single count because the import is idempotent: the
 * second run of the same week should report ten updates and zero creations, and a response that
 * only said "10 movies" would hide the difference.
 *
 * @param showingsRemoved upcoming showings deleted to make room. Never includes a showing with a
 *                        booking against it — those are somebody's tickets and are left alone.
 */
public record CatalogImportResponse(
        int moviesCreated,
        int moviesUpdated,
        int moviesSkipped,
        int showingsRemoved,
        int showingsCreated,
        List<ImportedMovie> movies
) {

    public record ImportedMovie(Long id, Long tmdbId, String title, String rating, int durationMinutes,
                                String genre, String posterUrl) {
    }
}
