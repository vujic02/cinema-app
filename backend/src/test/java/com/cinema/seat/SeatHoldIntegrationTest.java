package com.cinema.seat;

import com.cinema.support.SeatBroadcasts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The hold and release endpoints of TECH.md §5, including the WebSocket broadcasts they trigger.
 * The head-to-head race itself lives in {@link SeatHoldConcurrencyIntegrationTest}.
 */
class SeatHoldIntegrationTest extends AbstractSeatIntegrationTest {

    private static final Duration BROADCAST_TIMEOUT = Duration.ofSeconds(5);

    @Test
    @DisplayName("holding a seat returns the TTL and broadcasts HELD to the showing's topic")
    void holdsSeatAndBroadcasts() throws Exception {
        Auditorium hall = auditorium();

        try (SeatBroadcasts broadcasts = new SeatBroadcasts(brokerChannel)) {
            mockMvc.perform(post(holdUrl(hall.showingId(), hall.seat(0)))
                            .header("Authorization", customerBearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.seatId").value(hall.seat(0)))
                    .andExpect(jsonPath("$.label").value(hall.label(0)))
                    .andExpect(jsonPath("$.status").value("HELD"))
                    .andExpect(jsonPath("$.expiresInSeconds").value(300))
                    .andExpect(jsonPath("$.expiresAt").isNotEmpty());

            SeatBroadcasts.Broadcast broadcast = broadcasts.awaitNext(BROADCAST_TIMEOUT);
            assertThat(broadcast.destination()).isEqualTo("/topic/showings/" + hall.showingId());

            var payload = objectMapper.readTree(broadcast.payload());
            assertThat(payload.get("seatId").asLong()).isEqualTo(hall.seat(0));
            assertThat(payload.get("status").asText()).isEqualTo("HELD");
            // The topic is readable by anyone watching the showing, so it must not say who.
            assertThat(payload.has("userId")).isFalse();
        }
    }

    @Test
    @DisplayName("a seat another user holds comes back 409 SEAT_HELD")
    void rejectsSeatHeldByAnotherUser() throws Exception {
        Auditorium hall = auditorium();

        mockMvc.perform(post(holdUrl(hall.showingId(), hall.seat(1)))
                        .header("Authorization", customerBearer()))
                .andExpect(status().isOk());

        mockMvc.perform(post(holdUrl(hall.showingId(), hall.seat(1)))
                        .header("Authorization", bearerFor("sam@lumen.test", "password123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_HELD"));
    }

    @Test
    @DisplayName("re-holding your own seat succeeds without extending the hold")
    void reHoldingOwnSeatIsIdempotent() throws Exception {
        Auditorium hall = auditorium();
        String customer = customerBearer();

        mockMvc.perform(post(holdUrl(hall.showingId(), hall.seat(2))).header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(300));

        // Same answer, and crucially the countdown is what is left rather than a fresh 300 —
        // otherwise a user could sit on a seat forever by clicking it again.
        mockMvc.perform(post(holdUrl(hall.showingId(), hall.seat(2))).header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.expiresInSeconds").value(lessThanOrEqualTo(300)));
    }

    @Test
    @DisplayName("a sold seat cannot be held")
    void rejectsSoldSeat() throws Exception {
        Auditorium hall = auditorium();
        sell(hall.showingId(), hall.seat(3));

        mockMvc.perform(post(holdUrl(hall.showingId(), hall.seat(3)))
                        .header("Authorization", customerBearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_SOLD"));
    }

    @Test
    @DisplayName("releasing your hold frees the seat and broadcasts AVAILABLE")
    void releasesOwnHold() throws Exception {
        Auditorium hall = auditorium();
        String customer = customerBearer();

        mockMvc.perform(post(holdUrl(hall.showingId(), hall.seat(4))).header("Authorization", customer))
                .andExpect(status().isOk());

        try (SeatBroadcasts broadcasts = new SeatBroadcasts(brokerChannel)) {
            mockMvc.perform(delete(holdUrl(hall.showingId(), hall.seat(4))).header("Authorization", customer))
                    .andExpect(status().isNoContent());

            var payload = objectMapper.readTree(broadcasts.awaitNext(BROADCAST_TIMEOUT).payload());
            assertThat(payload.get("seatId").asLong()).isEqualTo(hall.seat(4));
            assertThat(payload.get("status").asText()).isEqualTo("AVAILABLE");
        }

        mockMvc.perform(get(seatMapUrl(hall.showingId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCount").value(6));
    }

    @Test
    @DisplayName("releasing a hold that already lapsed is a no-op, not an error")
    void releaseIsIdempotent() throws Exception {
        Auditorium hall = auditorium();

        // Nothing was ever held here. The frontend releases on navigate-away, which routinely
        // happens after the TTL has already fired.
        mockMvc.perform(delete(holdUrl(hall.showingId(), hall.seat(5)))
                        .header("Authorization", customerBearer()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("you cannot release someone else's hold")
    void rejectsReleasingAnotherUsersHold() throws Exception {
        Auditorium hall = auditorium();

        mockMvc.perform(post(holdUrl(hall.showingId(), hall.seat(0)))
                        .header("Authorization", customerBearer()))
                .andExpect(status().isOk());

        mockMvc.perform(delete(holdUrl(hall.showingId(), hall.seat(0)))
                        .header("Authorization", bearerFor("sam@lumen.test", "password123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLD_NOT_YOURS"));

        // And it survived the attempt.
        mockMvc.perform(get(seatMapUrl(hall.showingId())))
                .andExpect(jsonPath("$.rows[0].seats[0].status").value("HELD"));
    }

    @Test
    @DisplayName("a seat from another auditorium cannot be held against this showing")
    void rejectsSeatFromAnotherVenue() throws Exception {
        Auditorium hall = auditorium();
        Auditorium elsewhere = auditorium();

        mockMvc.perform(post(holdUrl(hall.showingId(), elsewhere.seat(0)))
                        .header("Authorization", customerBearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEAT_NOT_IN_SHOWING"));
    }

    @Test
    @DisplayName("seats cannot be held for a showing that has already started")
    void rejectsStartedShowing() throws Exception {
        Auditorium hall = auditorium();
        moveToPast(hall.showingId());

        mockMvc.perform(post(holdUrl(hall.showingId(), hall.seat(0)))
                        .header("Authorization", customerBearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHOWING_STARTED"));
    }

    @Test
    @DisplayName("holding needs an account even though reading the map does not")
    void holdRequiresAuthentication() throws Exception {
        Auditorium hall = auditorium();

        mockMvc.perform(post(holdUrl(hall.showingId(), hall.seat(0))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete(holdUrl(hall.showingId(), hall.seat(0))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(seatMapUrl(hall.showingId())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an unknown seat id is rejected before Redis is touched")
    void rejectsUnknownSeat() throws Exception {
        Auditorium hall = auditorium();

        mockMvc.perform(post(holdUrl(hall.showingId(), 999_999L))
                        .header("Authorization", customerBearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEAT_NOT_IN_SHOWING"));
    }
}
