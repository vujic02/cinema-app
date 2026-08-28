package com.cinema.catalog.tmdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The slice of TMDB's movie detail response this catalogue needs.
 *
 * <p>Field names are mapped explicitly rather than through a snake_case naming strategy: a
 * strategy would be a global Jackson setting affecting every DTO in the app, and these three
 * records are the only place in the codebase that speaks anyone else's JSON.
 *
 * <p>{@code ignoreUnknown} matters more than usual here — TMDB adds fields to this payload
 * without warning, and a third-party API growing a field is not a reason for an import to fail.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbMovie(
        Long id,
        String title,
        String overview,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("release_date") String releaseDate,

        /** Present on the detail call only; the list endpoints do not carry it. */
        Integer runtime,
        List<Genre> genres,

        /** Present only when the detail call asks for {@code append_to_response=release_dates}. */
        @JsonProperty("release_dates") ReleaseDates releaseDates
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Genre(Long id, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReleaseDates(List<CountryReleases> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CountryReleases(@JsonProperty("iso_3166_1") String country,
                                  @JsonProperty("release_dates") List<Release> releaseDates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Release(String certification) {
    }

    /**
     * The certification for one territory, e.g. "PG-13" in US.
     *
     * <p>TMDB returns one entry per release *type* per country — theatrical, digital, physical —
     * and only some of them carry a certification at all, so this takes the first non-blank one
     * rather than the first one.
     */
    public String certificationFor(String region) {
        if (releaseDates == null || releaseDates.results() == null) {
            return null;
        }
        return releaseDates.results().stream()
                .filter(country -> region.equalsIgnoreCase(country.country()))
                .flatMap(country -> country.releaseDates() == null
                        ? java.util.stream.Stream.<Release>empty()
                        : country.releaseDates().stream())
                .map(Release::certification)
                .filter(certification -> certification != null && !certification.isBlank())
                .findFirst()
                .orElse(null);
    }

    /** TMDB's own primary genre is the first in the list. */
    public String primaryGenre() {
        return genres == null || genres.isEmpty() ? null : genres.get(0).name();
    }
}
