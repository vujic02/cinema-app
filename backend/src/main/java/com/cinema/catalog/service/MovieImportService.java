package com.cinema.catalog.service;

import com.cinema.catalog.dto.CatalogImportResponse;
import com.cinema.catalog.tmdb.TmdbClient;
import com.cinema.catalog.tmdb.TmdbMovie;
import com.cinema.common.exception.ApiException;
import com.cinema.config.AppProperties;
import com.cinema.movie.domain.Movie;
import com.cinema.movie.repository.MovieRepository;
import com.cinema.showing.domain.Showing;
import com.cinema.showing.repository.ShowingRepository;
import com.cinema.venue.domain.Venue;
import com.cinema.venue.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Replaces the hand-written catalogue with what people are actually watching this week.
 *
 * <p>Two halves that have to happen together: importing the movies, and giving them showings.
 * A movie with no showing is invisible — the customer flow is driven entirely by
 * {@code GET /showings?date=} — so an import that only wrote {@code movies} rows would look
 * like it had done nothing at all.
 *
 * <p><b>Nothing is destroyed.</b> Seeded movies keep their rows, their past showings and every
 * booking against them, because {@code fk_showings_movie} and {@code fk_bookings_showing} have
 * no cascade and a sold ticket is not the importer's to delete. Only *upcoming* showings with no
 * bookings are cleared, and only when the caller asks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MovieImportService {

    /**
     * Showtimes, in UTC, matching the day boundary {@code ShowingService.findPublic} buckets on.
     * Four a day per venue is enough for the listing to look like a real cinema without turning
     * the seat map into a wall of near-identical chips.
     */
    private static final List<LocalTime> SLOTS =
            List.of(LocalTime.of(14, 0), LocalTime.of(17, 0), LocalTime.of(19, 30), LocalTime.of(22, 0));

    /** Only used where a venue has no showing history at all to inherit a price from. */
    private static final BigDecimal DEFAULT_PRICE = new BigDecimal("14.00");

    /** TMDB's chart is 20 long; asking for more than it holds is the caller's mistake. */
    private static final int MAX_MOVIES = 20;

    private final TmdbClient tmdb;
    private final AppProperties properties;
    private final MovieRepository movies;
    private final ShowingRepository showings;
    private final VenueRepository venues;

    /**
     * @param limit            how many of the week's trending movies to take
     * @param days             how many days of schedule to lay down, starting today
     * @param replaceUpcoming  clear the existing forward schedule first, so the listing is the
     *                         imported movies rather than a mixture with the seeded ones
     */
    @Transactional
    public CatalogImportResponse importFeatured(int limit, int days, boolean replaceUpcoming) {
        if (limit < 1 || limit > MAX_MOVIES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LIMIT",
                    "limit must be between 1 and " + MAX_MOVIES);
        }
        if (days < 1 || days > 14) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DAYS", "days must be between 1 and 14");
        }

        List<TmdbMovie> trending = tmdb.trendingThisWeek();
        if (trending.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_EMPTY_CHART",
                    "TMDB returned no trending movies. The catalogue was left unchanged.");
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<Movie> imported = new ArrayList<>();

        // Walk the chart rather than slicing it: a chart entry can be unusable (no artwork, no
        // runtime — usually something announced but unreleased), and stopping at the first ten
        // would then quietly deliver eight.
        for (TmdbMovie summary : trending) {
            if (imported.size() >= limit) {
                break;
            }
            Optional<TmdbMovie> detail = detailOf(summary);
            if (detail.isEmpty() || !usable(detail.get())) {
                skipped++;
                continue;
            }

            Optional<Movie> existing = movies.findByTmdbId(detail.get().id());
            Movie movie = apply(existing.orElseGet(Movie::new), detail.get());
            imported.add(movies.save(movie));
            if (existing.isPresent()) {
                updated++;
            } else {
                created++;
            }
        }

        if (imported.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_NO_USABLE_MOVIES",
                    "None of the trending movies had the artwork and runtime a listing needs.");
        }

        Instant now = Instant.now();
        int removed = replaceUpcoming ? clearUpcoming(now) : 0;
        int scheduled = schedule(imported, days, now);

        log.info("TMDB import: {} created, {} updated, {} skipped, {} showings removed, {} created",
                created, updated, skipped, removed, scheduled);

        return new CatalogImportResponse(created, updated, skipped, removed, scheduled,
                imported.stream()
                        .map(movie -> new CatalogImportResponse.ImportedMovie(
                                movie.getId(), movie.getTmdbId(), movie.getTitle(), movie.getRating(),
                                movie.getDurationMinutes(), movie.getGenre(), movie.getPosterUrl()))
                        .toList());
    }

    /**
     * One movie's full record. A single failure is swallowed rather than aborting the run: TMDB
     * occasionally 404s an id its own chart just returned, and losing the whole import over one
     * bad row would be worse than importing nine.
     */
    private Optional<TmdbMovie> detailOf(TmdbMovie summary) {
        if (summary.id() == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(tmdb.detail(summary.id()));
        } catch (ApiException failed) {
            // Auth and rate-limit failures are not per-movie problems — every remaining call
            // would fail the same way, so those stop the run.
            if (failed.getCode().equals("TMDB_UNAUTHORIZED") || failed.getCode().equals("TMDB_RATE_LIMITED")) {
                throw failed;
            }
            log.warn("Skipping TMDB movie {}: {}", summary.id(), failed.getMessage());
            return Optional.empty();
        }
    }

    /**
     * A listing needs a poster and a runtime. Anything missing either is an announcement rather
     * than a film that could be shown, and it would render as a blank card with "0m" under it.
     */
    private boolean usable(TmdbMovie movie) {
        return movie.id() != null
                && movie.title() != null && !movie.title().isBlank()
                && movie.runtime() != null && movie.runtime() > 0
                && properties.tmdb().posterUrl(movie.posterPath()) != null;
    }

    private Movie apply(Movie movie, TmdbMovie source) {
        movie.setTmdbId(source.id());
        movie.setTitle(truncate(source.title(), 200));
        movie.setDescription(truncate(blankToNull(source.overview()), 2000));
        movie.setDurationMinutes(source.runtime());
        movie.setGenre(truncate(defaultIfBlank(source.primaryGenre(), "Feature"), 60));
        // TMDB has no certification for a film not yet rated in the region, and the column is
        // NOT NULL. "NR" is what a cinema listing prints in that case.
        movie.setRating(truncate(defaultIfBlank(source.certificationFor(properties.tmdb().region()), "NR"), 10));
        movie.setPosterUrl(properties.tmdb().posterUrl(source.posterPath()));
        // Derived from the TMDB id so the fallback gradient is stable across re-imports and
        // distinct between neighbouring movies. 137 is coprime with 360, so consecutive ids land
        // far apart on the colour wheel instead of in a gradient of near-identical blues.
        movie.setPosterHue((int) Math.floorMod(source.id() * 137L, 360L));
        return movie;
    }

    /** @return how many upcoming showings were cleared to make room. */
    private int clearUpcoming(Instant now) {
        List<Showing> stale = showings.findUpcomingWithoutBookings(now);
        showings.deleteAll(stale);
        // Flushed here so the inserts below cannot collide with a row this delete is still
        // holding on uq_showings_venue_start.
        showings.flush();
        return stale.size();
    }

    /**
     * Lays the imported movies across every venue and slot, rotating so the same film is not
     * always the 19:30.
     *
     * <p>Slots already in the past are skipped — {@code SeatService} and {@code BookingService}
     * both refuse a started showing, so scheduling one creates a listing entry that can only
     * fail. Collisions on {@code uq_showings_venue_start} are skipped rather than thrown: with
     * {@code replaceUpcoming=false} the venue may legitimately already be busy at that time.
     */
    private int schedule(List<Movie> imported, int days, Instant now) {
        List<Venue> allVenues = venues.findAllByOrderByNameAsc();
        if (allVenues.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "NO_VENUES",
                    "There are no venues to schedule showings in.");
        }

        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        List<Showing> batch = new ArrayList<>();
        int rotation = 0;

        for (int day = 0; day < days; day++) {
            LocalDate date = today.plusDays(day);
            for (LocalTime slot : SLOTS) {
                for (Venue venue : allVenues) {
                    Instant startTime = date.atTime(slot).toInstant(ZoneOffset.UTC);
                    Movie movie = imported.get(rotation++ % imported.size());

                    if (!startTime.isAfter(now) || showings.existsByVenueIdAndStartTime(venue.getId(), startTime)) {
                        continue;
                    }
                    batch.add(Showing.builder()
                            .movie(movie)
                            .venue(venue)
                            .startTime(startTime)
                            .price(priceFor(venue))
                            .build());
                }
            }
        }

        showings.saveAll(batch);
        return batch.size();
    }

    /** Inherits what the venue already charges, so the IMAX stays dearer than the multiplex. */
    private BigDecimal priceFor(Venue venue) {
        return showings.findLatestPriceForVenue(venue.getId()).orElse(DEFAULT_PRICE);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
