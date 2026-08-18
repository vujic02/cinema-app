package com.cinema.seat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/showings/{id}/seat-map} — the read half of TECH.md §5. Status is assembled
 * from MySQL (sold) and Redis (held), so each test drives one of those and checks the map.
 */
class SeatMapIntegrationTest extends AbstractSeatIntegrationTest {

    @Test
    @DisplayName("the map comes back grouped into rows, in draw order, all available")
    void returnsFullLayout() throws Exception {
        Auditorium hall = auditorium();

        mockMvc.perform(get(seatMapUrl(hall.showingId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showingId").value(hall.showingId()))
                .andExpect(jsonPath("$.price").value(12.00))
                .andExpect(jsonPath("$.holdTtlSeconds").value(300))
                .andExpect(jsonPath("$.availableCount").value(6))
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.rows[0].rowLabel").value("A"))
                .andExpect(jsonPath("$.rows[1].rowLabel").value("B"))
                .andExpect(jsonPath("$.rows[0].seats.length()").value(3))
                .andExpect(jsonPath("$.rows[0].seats[0].label").value("A1"))
                .andExpect(jsonPath("$.rows[1].seats[2].label").value("B3"))
                .andExpect(jsonPath("$.rows[0].seats[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.rows[0].seats[0].heldByYou").value(false));
    }

    @Test
    @DisplayName("browsing the seat map needs no account")
    void readableAnonymously() throws Exception {
        Auditorium hall = auditorium();

        mockMvc.perform(get(seatMapUrl(hall.showingId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].seats[0].heldByYou").value(false));
    }

    @Test
    @DisplayName("a sold seat reads SOLD and drops out of the available count")
    void reflectsSoldSeats() throws Exception {
        Auditorium hall = auditorium();
        sell(hall.showingId(), hall.seat(1));

        mockMvc.perform(get(seatMapUrl(hall.showingId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCount").value(5))
                .andExpect(jsonPath("$.rows[0].seats[1].status").value("SOLD"))
                .andExpect(jsonPath("$.rows[0].seats[0].status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("a live Redis hold reads HELD, and only its owner sees heldByYou")
    void reflectsHolds() throws Exception {
        Auditorium hall = auditorium();
        String customer = customerBearer();

        mockMvc.perform(post(holdUrl(hall.showingId(), hall.seat(2)))
                        .header("Authorization", customer))
                .andExpect(status().isOk());

        // The holder sees their own seat flagged.
        mockMvc.perform(get(seatMapUrl(hall.showingId())).header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCount").value(5))
                .andExpect(jsonPath("$.rows[0].seats[2].status").value("HELD"))
                .andExpect(jsonPath("$.rows[0].seats[2].heldByYou").value(true));

        // Everyone else sees a held seat with no hint of who holds it.
        mockMvc.perform(get(seatMapUrl(hall.showingId()))
                        .header("Authorization", bearerFor("sam@lumen.test", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].seats[2].status").value("HELD"))
                .andExpect(jsonPath("$.rows[0].seats[2].heldByYou").value(false));
    }

    @Test
    @DisplayName("holds on one showing never leak into another showing's map")
    void holdsAreScopedToTheirShowing() throws Exception {
        Auditorium first = auditorium();
        Auditorium second = auditorium();

        mockMvc.perform(post(holdUrl(first.showingId(), first.seat(0)))
                        .header("Authorization", customerBearer()))
                .andExpect(status().isOk());

        mockMvc.perform(get(seatMapUrl(second.showingId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCount").value(6));
    }

    @Test
    @DisplayName("an unknown showing is a 404, not an empty map")
    void unknownShowingIsNotFound() throws Exception {
        mockMvc.perform(get(seatMapUrl(999_999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
