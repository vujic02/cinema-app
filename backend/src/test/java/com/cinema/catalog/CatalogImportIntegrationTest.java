package com.cinema.catalog;

import com.cinema.catalog.tmdb.TmdbClient;
import com.cinema.catalog.tmdb.TmdbMovie;
import com.cinema.common.exception.ApiException;
import com.cinema.movie.domain.Movie;
import com.cinema.movie.repository.MovieRepository;
import com.cinema.showing.domain.Showing;
import com.cinema.showing.repository.ShowingRepository;
import com.cinema.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The TMDB import, with TMDB itself replaced.
 *
 * <p>{@link TmdbClient} is mocked rather than pointed at a stub server: the client's own job
 * (URL grammar, auth style, error mapping) is a separate concern, and what matters here is
 * everything downstream of it — how a TMDB payload becomes a {@code movies} row, and what the
 * schedule rebuild is and is not allowed to delete. Mocking it also keeps the suite offline,
 * which is the whole reason the import is admin-triggered rather than run at startup.
 *
 * <p>{@code @Transactional} here, unlike the rest of the suite, because these tests genuinely
 * conflict with each other: the import is idempotent by tmdb_id and skips a venue slot that is
 * already busy, so a second test running after a first would see updates where it expected
 * creations and an already-full schedule where it expected an empty one. Rolling back after each
 * one is what makes "created 3" mean the same thing in every method.
 */
@Transactional
class CatalogImportIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private TmdbClient tmdb;

    @Autowired
    private MovieRepository movies;

    @Autowired
    private ShowingRepository showings;

    @BeforeEach
    void stubChart() {
        given(tmdb.trendingThisWeek()).willReturn(chartOf(900_001L, 900_002L, 900_003L));
        given(tmdb.detail(anyLong())).willAnswer(call -> detail(call.getArgument(0), 118, "/poster.jpg"));
    }

    @Test
    @DisplayName("imports the chart into movies with real artwork and a schedule to show them in")
    void importsMoviesAndSchedulesThem() throws Exception {
        var result = mockMvc.perform(post("/api/admin/catalog/import-featured")
                        .header("Authorization", adminBearer())
                        .param("limit", "3")
                        .param("days", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moviesCreated").value(3))
                .andExpect(jsonPath("$.moviesUpdated").value(0))
                .andExpect(jsonPath("$.movies[0].posterUrl").value("https://image.tmdb.org/t/p/w500/poster.jpg"))
                .andReturn();

        assertThat(asJson(result).get("showingsCreated").asInt()).isPositive();

        Movie imported = movies.findByTmdbId(900_001L).orElseThrow();
        assertThat(imported.getDurationMinutes()).isEqualTo(118);
        assertThat(imported.getGenre()).isEqualTo("Science Fiction");
        assertThat(imported.getRating()).isEqualTo("PG-13");
        assertThat(imported.getPosterUrl()).isEqualTo("https://image.tmdb.org/t/p/w500/poster.jpg");
        // The gradient fallback still has to be a legal hue even now that artwork exists.
        assertThat(imported.getPosterHue()).isBetween(0, 359);

        // The point of importing at all: the customer-facing listing serves them.
        assertThat(upcomingTitles()).contains(imported.getTitle());
    }

    @Test
    @DisplayName("re-running updates the same rows instead of duplicating them")
    void reRunIsIdempotent() throws Exception {
        importFeatured(3, 2, true);
        long afterFirst = movies.count();

        given(tmdb.detail(anyLong())).willAnswer(call -> detail(call.getArgument(0), 131, "/updated.jpg"));

        mockMvc.perform(post("/api/admin/catalog/import-featured")
                        .header("Authorization", adminBearer())
                        .param("limit", "3")
                        .param("days", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moviesCreated").value(0))
                .andExpect(jsonPath("$.moviesUpdated").value(3));

        assertThat(movies.count()).isEqualTo(afterFirst);
        assertThat(movies.findByTmdbId(900_001L).orElseThrow().getDurationMinutes()).isEqualTo(131);
    }

    @Test
    @DisplayName("clears the upcoming schedule but never a showing somebody has booked")
    void keepsBookedShowings() throws Exception {
        List<Long> bookedUpcoming = upcomingShowingIdsWithBookings();
        long freeUpcomingBefore = showings.findUpcomingWithoutBookings(Instant.now()).size();

        var result = importFeatured(3, 2, true);
        assertThat(asJson(result).get("showingsRemoved").asInt()).isEqualTo((int) freeUpcomingBefore);

        // Every booked showing is still there. fk_bookings_showing has no cascade, so deleting
        // one would fail loudly — but the query is what stops it being attempted at all.
        assertThat(showings.findAllById(bookedUpcoming)).hasSize(bookedUpcoming.size());
    }

    @Test
    @DisplayName("leaves the existing schedule alone when asked not to replace it")
    void respectsReplaceUpcomingFalse() throws Exception {
        long before = showings.count();

        mockMvc.perform(post("/api/admin/catalog/import-featured")
                        .header("Authorization", adminBearer())
                        .param("limit", "2")
                        .param("days", "1")
                        .param("replaceUpcoming", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showingsRemoved").value(0));

        assertThat(showings.count()).isGreaterThan(before);
    }

    @Test
    @DisplayName("a chart entry with no runtime or no poster is skipped, and the chart is walked for a replacement")
    void skipsUnusableEntriesAndKeepsCounting() throws Exception {
        given(tmdb.trendingThisWeek()).willReturn(chartOf(900_001L, 900_002L, 900_003L, 900_004L));
        given(tmdb.detail(900_001L)).willReturn(detail(900_001L, null, "/poster.jpg"));   // unreleased
        given(tmdb.detail(900_002L)).willReturn(detail(900_002L, 120, null));             // no artwork
        given(tmdb.detail(900_003L)).willReturn(detail(900_003L, 120, "/poster.jpg"));
        given(tmdb.detail(900_004L)).willReturn(detail(900_004L, 95, "/poster.jpg"));

        mockMvc.perform(post("/api/admin/catalog/import-featured")
                        .header("Authorization", adminBearer())
                        .param("limit", "2")
                        .param("days", "1"))
                .andExpect(status().isOk())
                // Two asked for, two delivered — the walk did not stop at the first two entries.
                .andExpect(jsonPath("$.moviesCreated").value(2))
                .andExpect(jsonPath("$.moviesSkipped").value(2));

        assertThat(movies.findByTmdbId(900_003L)).isPresent();
        assertThat(movies.findByTmdbId(900_004L)).isPresent();
        assertThat(movies.findByTmdbId(900_001L)).isEmpty();
    }

    @Test
    @DisplayName("one movie 404ing does not lose the whole import")
    void survivesOneBadDetailCall() throws Exception {
        willThrow(new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_UNAVAILABLE", "gone"))
                .given(tmdb).detail(900_002L);

        mockMvc.perform(post("/api/admin/catalog/import-featured")
                        .header("Authorization", adminBearer())
                        .param("limit", "3")
                        .param("days", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moviesCreated").value(2))
                .andExpect(jsonPath("$.moviesSkipped").value(1));
    }

    @Test
    @DisplayName("a rejected API key stops the run rather than skipping every movie in turn")
    void authFailureAborts() throws Exception {
        willThrow(new ApiException(HttpStatus.BAD_GATEWAY, "TMDB_UNAUTHORIZED", "bad key"))
                .given(tmdb).detail(anyLong());

        mockMvc.perform(post("/api/admin/catalog/import-featured")
                        .header("Authorization", adminBearer())
                        .param("limit", "3"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("TMDB_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("the import is admin-only")
    void customersCannotImport() throws Exception {
        mockMvc.perform(post("/api/admin/catalog/import-featured")
                        .header("Authorization", customerBearer()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/catalog/import-featured"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("limit and days are bounded")
    void rejectsOutOfRangeArguments() throws Exception {
        mockMvc.perform(post("/api/admin/catalog/import-featured")
                        .header("Authorization", adminBearer())
                        .param("limit", "50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LIMIT"));

        mockMvc.perform(post("/api/admin/catalog/import-featured")
                        .header("Authorization", adminBearer())
                        .param("days", "99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DAYS"));
    }

    @Test
    @DisplayName("the imported poster reaches every screen that draws one")
    void posterUrlIsServedOnEveryMovieCarryingDto() throws Exception {
        importFeatured(3, 2, true);
        Movie imported = movies.findByTmdbId(900_001L).orElseThrow();

        mockMvc.perform(get("/api/movies/" + imported.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posterUrl").value("https://image.tmdb.org/t/p/w500/poster.jpg"));

        Optional<Showing> scheduled = showings.findUpcomingWithoutBookings(Instant.now()).stream()
                .filter(showing -> showing.getMovie().getId().equals(imported.getId()))
                .findFirst();
        assertThat(scheduled).isPresent();

        mockMvc.perform(get("/api/showings/" + scheduled.orElseThrow().getId() + "/seat-map"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------------------------

    private org.springframework.test.web.servlet.MvcResult importFeatured(int limit, int days, boolean replace)
            throws Exception {
        return mockMvc.perform(post("/api/admin/catalog/import-featured")
                        .header("Authorization", adminBearer())
                        .param("limit", String.valueOf(limit))
                        .param("days", String.valueOf(days))
                        .param("replaceUpcoming", String.valueOf(replace)))
                .andExpect(status().isOk())
                .andReturn();
    }

    /** Titles the customer-facing listing would show over the next week. */
    private List<String> upcomingTitles() {
        return showings.findUpcomingWithoutBookings(Instant.now()).stream()
                .map(showing -> showing.getMovie().getTitle())
                .distinct()
                .toList();
    }

    private List<Long> upcomingShowingIdsWithBookings() {
        List<Long> free = showings.findUpcomingWithoutBookings(Instant.now()).stream()
                .map(Showing::getId)
                .toList();
        List<Long> booked = new ArrayList<>();
        Instant now = Instant.now();
        for (Showing showing : showings.findAll()) {
            if (showing.getStartTime().isAfter(now) && !free.contains(showing.getId())) {
                booked.add(showing.getId());
            }
        }
        return booked;
    }

    /** The summary shape TMDB's chart returns: ids and titles, no runtime or genres. */
    private static List<TmdbMovie> chartOf(long... ids) {
        List<TmdbMovie> chart = new ArrayList<>();
        for (long id : ids) {
            chart.add(new TmdbMovie(id, "Trending " + id, null, "/poster.jpg", "2026-08-01", null, null, null));
        }
        return chart;
    }

    private static TmdbMovie detail(long id, Integer runtime, String posterPath) {
        return new TmdbMovie(id, "Trending " + id, "A description of " + id, posterPath, "2026-08-01",
                runtime,
                List.of(new TmdbMovie.Genre(878L, "Science Fiction")),
                new TmdbMovie.ReleaseDates(List.of(new TmdbMovie.CountryReleases("US",
                        List.of(new TmdbMovie.Release(""), new TmdbMovie.Release("PG-13"))))));
    }
}
