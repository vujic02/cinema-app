package com.cinema.catalog;

import com.cinema.catalog.tmdb.TmdbMovie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two bits of TMDB's payload that need interpreting rather than copying.
 *
 * <p>Both are shaped by how TMDB actually answers: certifications are nested three deep, keyed by
 * territory and split by release type, and most of the entries carry an empty string rather than
 * being absent.
 */
class TmdbMovieTest {

    @Test
    @DisplayName("the certification comes from the requested region")
    void picksRegionCertification() {
        TmdbMovie movie = withCertifications(
                release("GB", "15"),
                release("US", "R"),
                release("DE", "16"));

        assertThat(movie.certificationFor("US")).isEqualTo("R");
        assertThat(movie.certificationFor("GB")).isEqualTo("15");
    }

    @Test
    @DisplayName("blank certifications are skipped, not returned")
    void skipsBlankCertifications() {
        // TMDB lists one entry per release type — theatrical, digital, physical — and typically
        // only one of them carries the rating. Taking the *first* entry would return "".
        TmdbMovie movie = withCertifications(new TmdbMovie.CountryReleases("US", List.of(
                new TmdbMovie.Release(""),
                new TmdbMovie.Release("   "),
                new TmdbMovie.Release("PG-13"))));

        assertThat(movie.certificationFor("US")).isEqualTo("PG-13");
    }

    @Test
    @DisplayName("a movie with no certification in the region gives back nothing")
    void missingRegionYieldsNull() {
        TmdbMovie movie = withCertifications(release("FR", "12"));
        assertThat(movie.certificationFor("US")).isNull();
    }

    @Test
    @DisplayName("release dates are absent entirely unless the detail call asked for them")
    void toleratesMissingReleaseDates() {
        TmdbMovie movie = new TmdbMovie(1L, "Untitled", null, "/p.jpg", null, 100, List.of(), null);
        assertThat(movie.certificationFor("US")).isNull();
    }

    @Test
    @DisplayName("the primary genre is the first one TMDB lists")
    void primaryGenreIsTheFirst() {
        TmdbMovie movie = new TmdbMovie(1L, "Untitled", null, "/p.jpg", null, 100,
                List.of(new TmdbMovie.Genre(878L, "Science Fiction"), new TmdbMovie.Genre(12L, "Adventure")),
                null);

        assertThat(movie.primaryGenre()).isEqualTo("Science Fiction");
    }

    @Test
    @DisplayName("a movie with no genres has no primary genre")
    void noGenresYieldsNull() {
        assertThat(new TmdbMovie(1L, "T", null, "/p.jpg", null, 100, List.of(), null).primaryGenre()).isNull();
        assertThat(new TmdbMovie(1L, "T", null, "/p.jpg", null, 100, null, null).primaryGenre()).isNull();
    }

    private static TmdbMovie.CountryReleases release(String country, String certification) {
        return new TmdbMovie.CountryReleases(country, List.of(new TmdbMovie.Release(certification)));
    }

    private static TmdbMovie withCertifications(TmdbMovie.CountryReleases... countries) {
        return new TmdbMovie(1L, "Untitled", "overview", "/poster.jpg", "2026-08-01", 120,
                List.of(new TmdbMovie.Genre(28L, "Action")),
                new TmdbMovie.ReleaseDates(List.of(countries)));
    }
}
