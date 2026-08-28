package com.cinema.movie.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Bounds mirror the column definitions and CHECK constraints in the initial migration, so a bad
 * payload fails as a 400 with field errors rather than as a 409 from the database.
 *
 * @param posterHue 0–359, drives the poster gradient shown while the artwork loads, and the
 *                  whole poster where there is none
 * @param posterUrl optional artwork URL. The TMDB importer fills this in; a movie added by hand
 *                  may leave it out and fall back to the gradient
 */
public record MovieRequest(

        @NotBlank
        @Size(max = 200)
        String title,

        @Size(max = 2000)
        String description,

        @Min(1)
        @Max(600)
        int durationMinutes,

        @NotBlank
        @Size(max = 60)
        String genre,

        @NotBlank
        @Size(max = 10)
        String rating,

        @Min(0)
        @Max(359)
        int posterHue,

        @Size(max = 500)
        String posterUrl
) {
}
