package com.cinema.seat;

import com.cinema.auth.repository.UserRepository;
import com.cinema.booking.domain.Booking;
import com.cinema.booking.domain.BookingStatus;
import com.cinema.booking.repository.BookingRepository;
import com.cinema.seat.service.SeatHoldStore;
import com.cinema.showing.repository.ShowingRepository;
import com.cinema.support.AbstractIntegrationTest;
import com.cinema.venue.repository.SeatRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared rig for the Part 4 tests: each one builds its own auditorium and its own showing, so
 * seat statuses can be asserted exactly without the seed data — or another test class sharing
 * the container — moving the numbers.
 */
public abstract class AbstractSeatIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    protected BookingRepository bookings;

    @Autowired
    protected ShowingRepository showings;

    @Autowired
    protected SeatRepository seats;

    @Autowired
    protected UserRepository users;

    @Autowired
    protected TransactionTemplate transactions;

    /** Lets a test inspect or tear down a hold without going through the API. */
    @Autowired
    protected SeatHoldStore holdStore;

    /** What {@code SimpMessagingTemplate} publishes on; {@link com.cinema.support.SeatBroadcasts} listens here. */
    @Autowired
    @Qualifier("brokerChannel")
    protected AbstractSubscribableChannel brokerChannel;

    /**
     * A venue with a known layout and one upcoming showing in it.
     *
     * @param seatIds every seat id in draw order, so a test can say "the third seat"
     */
    public record Auditorium(long venueId, long showingId, List<Long> seatIds, List<String> labels) {

        public long seat(int index) {
            return seatIds.get(index);
        }

        public String label(int index) {
            return labels.get(index);
        }
    }

    /** Two rows of three, which is enough to prove row grouping without a wall of assertions. */
    protected Auditorium auditorium() throws Exception {
        return auditorium(futureSlot());
    }

    protected Auditorium auditorium(Instant startTime) throws Exception {
        String admin = adminBearer();

        long venueId = asJson(mockMvc.perform(post("/api/admin/venues")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Seat Venue %s","address":"1 Test Street"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();

        var layout = asJson(mockMvc.perform(put("/api/admin/venues/" + venueId + "/layout")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rows":[{"seatCount":3},{"seatCount":3}]}
                                """))
                .andExpect(status().isOk())
                .andReturn());

        List<Long> seatIds = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        layout.get("rows").forEach(row -> row.get("seats").forEach(seat -> {
            seatIds.add(seat.get("id").asLong());
            labels.add(seat.get("label").asText());
        }));

        long movieId = asJson(mockMvc.perform(post("/api/admin/movies")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hold Test %s","description":"","durationMinutes":100,
                                 "genre":"Drama","rating":"PG","posterHue":200}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();

        long showingId = asJson(mockMvc.perform(post("/api/admin/showings")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"movieId":%d,"venueId":%d,"startTime":"%s","price":12.00}
                                """.formatted(movieId, venueId, startTime)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();

        return new Auditorium(venueId, showingId, List.copyOf(seatIds), List.copyOf(labels));
    }

    /**
     * Writes a SOLD booking straight through the repository. Part 5 owns the checkout endpoint;
     * Part 4 only needs a seat that is already gone.
     * <p>
     * Wrapped in a transaction so the {@code getReferenceById} proxies are resolved by the same
     * persistence context that saves them — with {@code open-in-view=false} there is no ambient
     * one to fall back on.
     */
    protected void sell(long showingId, long seatId) {
        transactions.executeWithoutResult(status -> bookings.save(Booking.builder()
                .bookingReference("TEST-" + showingId + "-" + seatId)
                .showing(showings.getReferenceById(showingId))
                .seat(seats.getReferenceById(seatId))
                .user(users.findByEmailIgnoreCase(CUSTOMER_EMAIL).orElseThrow())
                .status(BookingStatus.SOLD)
                .pricePaid(new BigDecimal("12.00"))
                .build()));
    }

    /**
     * Backdates a showing. The admin API refuses to <em>create</em> one in the past, so the only
     * honest way to test the "this showing has already started" guard is to move an existing one.
     */
    protected void moveToPast(long showingId) {
        transactions.executeWithoutResult(status -> showings.findById(showingId)
                .orElseThrow()
                .setStartTime(Instant.now().minus(2, ChronoUnit.HOURS)));
    }

    /** Far enough out that no seeded showing shares the slot, and whole seconds for equality. */
    protected Instant futureSlot() {
        return Instant.now()
                .plus(120, ChronoUnit.DAYS)
                .truncatedTo(ChronoUnit.SECONDS);
    }

    protected String seatMapUrl(long showingId) {
        return "/api/showings/" + showingId + "/seat-map";
    }

    protected String holdUrl(long showingId, long seatId) {
        return "/api/showings/" + showingId + "/seats/" + seatId + "/hold";
    }

    /** Places real holds through the API, which is what a checkout has to be sitting on. */
    protected void hold(Auditorium hall, String bearer, int... seatIndexes) throws Exception {
        for (int index : seatIndexes) {
            mockMvc.perform(post(holdUrl(hall.showingId(), hall.seat(index)))
                            .header("Authorization", bearer))
                    .andExpect(status().isOk());
        }
    }

    protected String checkoutBody(long showingId, long... seatIds) {
        String ids = LongStream.of(seatIds).mapToObj(String::valueOf).collect(Collectors.joining(","));
        return """
                {"showingId":%d,"seatIds":[%s]}
                """.formatted(showingId, ids);
    }
}
