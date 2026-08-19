package com.cinema.seat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The per-user hold cap. Without it a single caller can take an entire auditorium for the full
 * TTL — no race required, just a loop — and keep renewing as each seat lapses.
 * <p>
 * Runs at a limit of 2 so the boundary is reached in three requests instead of eleven; the
 * property override gives this class its own context, with the containers still shared.
 */
@TestPropertySource(properties = "app.seat-hold.max-seats-per-user=2")
class SeatHoldLimitIntegrationTest extends AbstractSeatIntegrationTest {

    @Test
    @DisplayName("a third seat is refused once two are already held")
    void capsHoldsPerUser() throws Exception {
        Auditorium hall = auditorium();
        String customer = customerBearer();

        hold(hall, customer, 0, 1);

        mockMvc.perform(post(holdUrl(hall.showingId(), hall.seat(2))).header("Authorization", customer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLD_LIMIT_REACHED"));
    }

    @Test
    @DisplayName("releasing one frees an allowance for another")
    void releasingFreesAllowance() throws Exception {
        Auditorium hall = auditorium();
        String customer = customerBearer();

        hold(hall, customer, 0, 1);
        mockMvc.perform(delete(holdUrl(hall.showingId(), hall.seat(0))).header("Authorization", customer))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(holdUrl(hall.showingId(), hall.seat(2))).header("Authorization", customer))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("re-holding a seat you already hold is exempt from the cap")
    void reHoldingOwnSeatIsExempt() throws Exception {
        Auditorium hall = auditorium();
        String customer = customerBearer();

        hold(hall, customer, 0, 1);

        // At the limit, but this adds nothing to the total — rejecting it would break the
        // idempotent re-click that Part 4 relies on.
        mockMvc.perform(post(holdUrl(hall.showingId(), hall.seat(1))).header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HELD"));
    }

    @Test
    @DisplayName("the cap is per showing, not across the whole account")
    void capIsPerShowing() throws Exception {
        Auditorium first = auditorium();
        Auditorium second = auditorium();
        String customer = customerBearer();

        hold(first, customer, 0, 1);

        // Same user, different screening: their allowance there is untouched.
        mockMvc.perform(post(holdUrl(second.showingId(), second.seat(0))).header("Authorization", customer))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("one user at their limit does not block anybody else")
    void capIsPerUser() throws Exception {
        Auditorium hall = auditorium();
        String customer = customerBearer();

        hold(hall, customer, 0, 1);

        mockMvc.perform(post(holdUrl(hall.showingId(), hall.seat(2)))
                        .header("Authorization", bearerFor("sam@lumen.test", "password123")))
                .andExpect(status().isOk());
    }
}
