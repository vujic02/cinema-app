package com.cinema.booking;

import com.cinema.seat.AbstractSeatIntegrationTest;
import com.cinema.support.SeatBroadcasts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/bookings} — TECH.md §5 step 5. Reuses the Part 4 rig, because a checkout is
 * only meaningful on top of a hold.
 */
class CheckoutIntegrationTest extends AbstractSeatIntegrationTest {

    private static final Duration BROADCAST_TIMEOUT = Duration.ofSeconds(5);

    @Test
    @DisplayName("held seats become one booking, with the reference and total the server decides")
    void confirmsHeldSeats() throws Exception {
        Auditorium hall = auditorium();
        String customer = customerBearer();
        hold(hall, customer, 0, 1);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(hall.showingId(), hall.seat(0), hall.seat(1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.startsWith("LUM-")))
                .andExpect(jsonPath("$.status").value("SOLD"))
                .andExpect(jsonPath("$.seats.length()").value(2))
                .andExpect(jsonPath("$.seats[0].label").value(hall.label(0)))
                // 2 x the showing price of 12.00, computed from showings.price and never from
                // anything the client sent.
                .andExpect(jsonPath("$.total").value(24.00))
                .andExpect(jsonPath("$.upcoming").value(true))
                // The buyer's own booking must not carry customer details back to them.
                .andExpect(jsonPath("$.customer").doesNotExist());
    }

    @Test
    @DisplayName("confirming releases the holds and broadcasts SOLD")
    void broadcastsSold() throws Exception {
        Auditorium hall = auditorium();
        String customer = customerBearer();
        hold(hall, customer, 2);

        try (SeatBroadcasts broadcasts = new SeatBroadcasts(brokerChannel)) {
            mockMvc.perform(post("/api/bookings")
                            .header("Authorization", customer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(checkoutBody(hall.showingId(), hall.seat(2))))
                    .andExpect(status().isCreated());

            var payload = objectMapper.readTree(broadcasts.awaitNext(BROADCAST_TIMEOUT).payload());
            assertThat(payload.get("seatId").asLong()).isEqualTo(hall.seat(2));
            assertThat(payload.get("status").asText()).isEqualTo("SOLD");
        }

        // The hold key is gone, and the seat map now reports the sale from MySQL.
        assertThat(holdStore.holderOf(new com.cinema.seat.domain.HoldKey(hall.showingId(), hall.seat(2))))
                .isEmpty();
        mockMvc.perform(get(seatMapUrl(hall.showingId())))
                .andExpect(jsonPath("$.availableCount").value(5))
                .andExpect(jsonPath("$.rows[0].seats[2].status").value("SOLD"));
    }

    @Test
    @DisplayName("confirming the same basket twice is rejected")
    void rejectsDoubleConfirm() throws Exception {
        Auditorium hall = auditorium();
        String customer = customerBearer();
        hold(hall, customer, 0);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(hall.showingId(), hall.seat(0))))
                .andExpect(status().isCreated());

        // The first confirm deleted the hold and wrote the sale, so the replay hits the
        // already-sold check before it ever looks at Redis.
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(hall.showingId(), hall.seat(0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_SOLD"));

        // And exactly one row exists for that seat.
        assertThat(bookings.findSoldSeatIds(hall.showingId())).containsExactly(hall.seat(0));
    }

    @Test
    @DisplayName("a seat that was never held cannot be bought")
    void rejectsUnheldSeat() throws Exception {
        Auditorium hall = auditorium();

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", customerBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(hall.showingId(), hall.seat(0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));
    }

    @Test
    @DisplayName("an expired hold is rejected rather than silently re-acquired")
    void rejectsExpiredHold() throws Exception {
        Auditorium hall = auditorium();
        String customer = customerBearer();
        hold(hall, customer, 1);

        // Stands in for the TTL firing while the customer sat on the checkout screen.
        holdStore.forceRelease(new com.cinema.seat.domain.HoldKey(hall.showingId(), hall.seat(1)));

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(hall.showingId(), hall.seat(1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));

        assertThat(bookings.findSoldSeatIds(hall.showingId())).isEmpty();
    }

    @Test
    @DisplayName("you cannot buy a seat somebody else is holding")
    void rejectsAnotherUsersHold() throws Exception {
        Auditorium hall = auditorium();
        hold(hall, customerBearer(), 3);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearerFor("sam@lumen.test", "password123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(hall.showingId(), hall.seat(3))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_HELD"));
    }

    @Test
    @DisplayName("a basket is all-or-nothing: one bad seat writes none of them")
    void rejectsPartialBasket() throws Exception {
        Auditorium hall = auditorium();
        String customer = customerBearer();
        // Only the first seat is held; the second was never picked up.
        hold(hall, customer, 0);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(hall.showingId(), hall.seat(0), hall.seat(1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));

        // Nothing was written, and the good seat is still held rather than consumed.
        assertThat(bookings.findSoldSeatIds(hall.showingId())).isEmpty();
        assertThat(holdStore.holderOf(new com.cinema.seat.domain.HoldKey(hall.showingId(), hall.seat(0))))
                .isPresent();
    }

    @Test
    @DisplayName("a duplicated seat in the basket buys one ticket, not two")
    void collapsesDuplicateSeats() throws Exception {
        Auditorium hall = auditorium();
        String customer = customerBearer();
        hold(hall, customer, 4);

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(hall.showingId(), hall.seat(4), hall.seat(4))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seats.length()").value(1))
                .andExpect(jsonPath("$.total").value(12.00));
    }

    @Test
    @DisplayName("a seat from another auditorium is rejected before anything is written")
    void rejectsSeatFromAnotherVenue() throws Exception {
        Auditorium hall = auditorium();
        Auditorium elsewhere = auditorium();

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", customerBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(hall.showingId(), elsewhere.seat(0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEAT_NOT_IN_SHOWING"));
    }

    @Test
    @DisplayName("tickets cannot be bought for a showing that has already started")
    void rejectsStartedShowing() throws Exception {
        Auditorium hall = auditorium();
        String customer = customerBearer();
        hold(hall, customer, 0);
        moveToPast(hall.showingId());

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(hall.showingId(), hall.seat(0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHOWING_STARTED"));
    }

    @Test
    @DisplayName("checkout requires a token and a non-empty basket")
    void validatesRequest() throws Exception {
        Auditorium hall = auditorium();

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(hall.showingId(), hall.seat(0))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", customerBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showingId":%d,"seatIds":[]}
                                """.formatted(hall.showingId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.seatIds").isNotEmpty());
    }

    @Test
    @DisplayName("every seat of one purchase shares a single reference")
    void oneReferencePerPurchase() throws Exception {
        Auditorium hall = auditorium();
        String customer = customerBearer();
        hold(hall, customer, 0, 1, 2);

        var result = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(hall.showingId(), hall.seat(0), hall.seat(1), hall.seat(2))))
                .andExpect(status().isCreated())
                .andReturn();

        String reference = asJson(result).get("reference").asText();
        List<String> references = bookings.findByBookingReference(reference).stream()
                .map(booking -> booking.getBookingReference())
                .collect(Collectors.toList());

        assertThat(references).hasSize(3).containsOnly(reference);
    }
}
