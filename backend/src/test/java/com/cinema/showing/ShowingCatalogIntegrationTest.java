package com.cinema.showing;

import com.cinema.booking.repository.BookingRepository;
import com.cinema.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Showing CRUD plus the {@code ShowingSpecifications} filters.
 * <p>
 * Every assertion is scoped to a venue this test creates, so the seeded schedule — and anything
 * another test adds to the shared container — cannot change the expected counts.
 */
class ShowingCatalogIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BookingRepository bookings;

    @Test
    @DisplayName("a showing is scheduled and comes back with its movie and venue inlined")
    void createsShowing() throws Exception {
        long venueId = createVenue();
        long movieId = createMovie("Inlined");
        Instant start = futureSlot(10);

        mockMvc.perform(post("/api/admin/showings")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(showingBody(movieId, venueId, start, "12.50")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.price").value(12.50))
                .andExpect(jsonPath("$.movie.title").value("Inlined"))
                .andExpect(jsonPath("$.movie.posterHue").value(120))
                .andExpect(jsonPath("$.venue.id").value(venueId));
    }

    @Test
    @DisplayName("one venue cannot run two showings at the same instant")
    void rejectsDoubleBookedSlot() throws Exception {
        long venueId = createVenue();
        long movieId = createMovie("Clash");
        Instant start = futureSlot(11);

        createShowing(movieId, venueId, start);

        mockMvc.perform(post("/api/admin/showings")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(showingBody(movieId, venueId, start, "10.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHOWING_SLOT_TAKEN"));
    }

    @Test
    @DisplayName("a showing keeping its own slot can still be repriced")
    void updatesShowingInPlace() throws Exception {
        long venueId = createVenue();
        long movieId = createMovie("Repriced");
        Instant start = futureSlot(12);
        long showingId = createShowing(movieId, venueId, start);

        mockMvc.perform(put("/api/admin/showings/" + showingId)
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(showingBody(movieId, venueId, start, "19.99")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(19.99));
    }

    @Test
    @DisplayName("a showing in the past is rejected on create")
    void rejectsPastShowing() throws Exception {
        long venueId = createVenue();
        long movieId = createMovie("Yesterday");

        mockMvc.perform(post("/api/admin/showings")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(showingBody(movieId, venueId, Instant.now().minus(2, ChronoUnit.DAYS), "10.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SHOWING_IN_THE_PAST"));
    }

    @Test
    @DisplayName("a negative price and a missing movie are caught before the database")
    void validatesShowingRequest() throws Exception {
        long venueId = createVenue();
        long movieId = createMovie("Invalid");

        mockMvc.perform(post("/api/admin/showings")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(showingBody(movieId, venueId, futureSlot(13), "-1.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.price").isNotEmpty());

        mockMvc.perform(post("/api/admin/showings")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(showingBody(999_999L, venueId, futureSlot(14), "10.00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("an unsold showing deletes; one with bookings does not")
    void guardsDeletion() throws Exception {
        long venueId = createVenue();
        long movieId = createMovie("Deletable");
        long showingId = createShowing(movieId, venueId, futureSlot(15));

        mockMvc.perform(delete("/api/admin/showings/" + showingId).header("Authorization", adminBearer()))
                .andExpect(status().isNoContent());

        // The seeded LUM-40218 sale is against a Downtown 8 showing today at 19:00.
        long soldShowingId = seededSoldShowingId();
        mockMvc.perform(delete("/api/admin/showings/" + soldShowingId).header("Authorization", adminBearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHOWING_HAS_BOOKINGS"));
    }

    // ------------------------------------------------------- specification filters

    @Test
    @DisplayName("the venue filter returns only that venue's schedule")
    void filtersByVenue() throws Exception {
        long venueId = createVenue();
        long movieId = createMovie("Venue filter");
        createShowing(movieId, venueId, futureSlot(20));
        createShowing(movieId, venueId, futureSlot(21));

        mockMvc.perform(get("/api/showings").param("venueId", String.valueOf(venueId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].venue.id").value(venueId));
    }

    @Test
    @DisplayName("the movie filter narrows within a venue")
    void filtersByMovie() throws Exception {
        long venueId = createVenue();
        long wanted = createMovie("Wanted");
        long other = createMovie("Other");
        createShowing(wanted, venueId, futureSlot(22));
        createShowing(other, venueId, futureSlot(23));

        mockMvc.perform(get("/api/showings")
                        .param("venueId", String.valueOf(venueId))
                        .param("movieId", String.valueOf(wanted)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].movie.id").value(wanted));
    }

    @Test
    @DisplayName("the date filter is a single UTC day, excluding the next one")
    void filtersBySingleDay() throws Exception {
        long venueId = createVenue();
        long movieId = createMovie("Day filter");

        Instant day = futureSlot(30);
        createShowing(movieId, venueId, day);
        createShowing(movieId, venueId, day.plus(1, ChronoUnit.DAYS));

        LocalDate wanted = day.atZone(ZoneOffset.UTC).toLocalDate();

        mockMvc.perform(get("/api/showings")
                        .param("venueId", String.valueOf(venueId))
                        .param("date", wanted.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("results come back in start-time order regardless of insertion order")
    void ordersByStartTime() throws Exception {
        long venueId = createVenue();
        long movieId = createMovie("Ordering");

        Instant later = futureSlot(41);
        Instant earlier = futureSlot(40);
        createShowing(movieId, venueId, later);
        createShowing(movieId, venueId, earlier);

        mockMvc.perform(get("/api/showings").param("venueId", String.valueOf(venueId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].startTime").value(earlier.toString()))
                .andExpect(jsonPath("$[1].startTime").value(later.toString()));
    }

    @Test
    @DisplayName("the admin range filter is inclusive at both ends")
    void filtersByInclusiveDateRange() throws Exception {
        long venueId = createVenue();
        long movieId = createMovie("Range");

        Instant first = futureSlot(50);
        createShowing(movieId, venueId, first);
        createShowing(movieId, venueId, first.plus(1, ChronoUnit.DAYS));
        createShowing(movieId, venueId, first.plus(2, ChronoUnit.DAYS));

        LocalDate from = first.atZone(ZoneOffset.UTC).toLocalDate();

        mockMvc.perform(get("/api/admin/showings")
                        .header("Authorization", adminBearer())
                        .param("venueId", String.valueOf(venueId))
                        .param("from", from.toString())
                        .param("to", from.plusDays(1).toString()))
                .andExpect(status().isOk())
                // Both boundary days included, the third excluded.
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("the public listing hides showings that have already started")
    void publicListingIsUpcomingOnly() throws Exception {
        long venueId = createVenue();
        long movieId = createMovie("Upcoming only");
        createShowing(movieId, venueId, futureSlot(60));

        // Written straight through the admin update path, which allows a past start time.
        long pastId = createShowing(movieId, venueId, futureSlot(61));
        mockMvc.perform(put("/api/admin/showings/" + pastId)
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(showingBody(movieId, venueId, Instant.now().minus(3, ChronoUnit.DAYS), "10.00")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/showings").param("venueId", String.valueOf(venueId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // The admin view, with no date bounds, still sees both.
        mockMvc.perform(get("/api/admin/showings")
                        .header("Authorization", adminBearer())
                        .param("venueId", String.valueOf(venueId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ----------------------------------------------------------------- helpers

    /** Distinct future instants, whole seconds, so no two tests collide on the venue-slot key. */
    private Instant futureSlot(int offsetHours) {
        return Instant.now().plus(90, ChronoUnit.DAYS)
                .plus(offsetHours, ChronoUnit.HOURS)
                .truncatedTo(ChronoUnit.SECONDS);
    }

    private String showingBody(long movieId, long venueId, Instant startTime, String price) {
        return """
                {"movieId":%d,"venueId":%d,"startTime":"%s","price":%s}
                """.formatted(movieId, venueId, startTime.toString(), price);
    }

    private long createVenue() throws Exception {
        var result = mockMvc.perform(post("/api/admin/venues")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Schedule Venue %s","address":"1 Test Street"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();
        return asJson(result).get("id").asLong();
    }

    private long createMovie(String title) throws Exception {
        var result = mockMvc.perform(post("/api/admin/movies")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","description":"","durationMinutes":100,
                                 "genre":"Drama","rating":"PG","posterHue":120}
                                """.formatted(title)))
                .andExpect(status().isCreated())
                .andReturn();
        return asJson(result).get("id").asLong();
    }

    private long createShowing(long movieId, long venueId, Instant start) throws Exception {
        ResultActions result = mockMvc.perform(post("/api/admin/showings")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(showingBody(movieId, venueId, start, "10.00")))
                .andExpect(status().isCreated());
        return asJson(result.andReturn()).get("id").asLong();
    }

    /**
     * A showing the seed sold tickets for, read from the bookings themselves rather than guessed
     * from the schedule. {@code getShowing().getId()} reads the foreign key off the lazy proxy
     * without initialising it, so no transaction is needed.
     */
    private long seededSoldShowingId() {
        return bookings.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Seed data has no bookings"))
                .getShowing()
                .getId();
    }
}
