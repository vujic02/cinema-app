package com.cinema.booking;

import com.cinema.seat.AbstractSeatIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/bookings/me} and {@code GET /api/bookings/{ref}} — the customer's own history.
 */
class BookingHistoryIntegrationTest extends AbstractSeatIntegrationTest {

    @Test
    @DisplayName("a purchase appears under upcoming, folded into one entry with all its seats")
    void listsUpcomingBookings() throws Exception {
        Auditorium hall = auditorium();
        String customer = customerBearer();
        hold(hall, customer, 0, 1);
        String reference = buy(hall, customer, hall.seat(0), hall.seat(1));

        mockMvc.perform(get("/api/bookings/me").header("Authorization", customer))
                .andExpect(status().isOk())
                // Scoped to the reference this test created: the seeded bookings and anything
                // another test class adds share the same customer account.
                .andExpect(jsonPath("$.upcoming[?(@.reference == '" + reference + "')].seats.length()")
                        .value(2))
                .andExpect(jsonPath("$.upcoming[?(@.reference == '" + reference + "')].total")
                        .value(24.00))
                .andExpect(jsonPath("$.past[?(@.reference == '" + reference + "')]").isEmpty());
    }

    @Test
    @DisplayName("once the showing has started the same booking moves to past")
    void splitsOnShowingStartTime() throws Exception {
        Auditorium hall = auditorium();
        String customer = customerBearer();
        hold(hall, customer, 2);
        String reference = buy(hall, customer, hall.seat(2));

        moveToPast(hall.showingId());

        mockMvc.perform(get("/api/bookings/me").header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming[?(@.reference == '" + reference + "')]").isEmpty())
                .andExpect(jsonPath("$.past[?(@.reference == '" + reference + "')].seats.length()")
                        .value(1));
    }

    @Test
    @DisplayName("a booking is readable by its reference, with the seats in seat order")
    void readsByReference() throws Exception {
        Auditorium hall = auditorium();
        String customer = customerBearer();
        // Held out of order on purpose — the ticket should still read A1, A2, A3.
        hold(hall, customer, 2, 0, 1);
        String reference = buy(hall, customer, hall.seat(2), hall.seat(0), hall.seat(1));

        mockMvc.perform(get("/api/bookings/" + reference).header("Authorization", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value(reference))
                .andExpect(jsonPath("$.seats[0].label").value(hall.label(0)))
                .andExpect(jsonPath("$.seats[1].label").value(hall.label(1)))
                .andExpect(jsonPath("$.seats[2].label").value(hall.label(2)))
                .andExpect(jsonPath("$.customer").doesNotExist());
    }

    @Test
    @DisplayName("someone else's reference is a 404, not a 403")
    void hidesOtherPeoplesBookings() throws Exception {
        Auditorium hall = auditorium();
        String customer = customerBearer();
        hold(hall, customer, 0);
        String reference = buy(hall, customer, hall.seat(0));

        // A 403 would confirm the reference is real, which makes booking codes guessable.
        mockMvc.perform(get("/api/bookings/" + reference)
                        .header("Authorization", bearerFor("sam@lumen.test", "password123")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // An entirely made-up reference is indistinguishable from it.
        mockMvc.perform(get("/api/bookings/LUM-ZZZZZZ").header("Authorization", customer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("an admin can read any booking, and sees who bought it")
    void adminReadsAnyBooking() throws Exception {
        Auditorium hall = auditorium();
        String customer = customerBearer();
        hold(hall, customer, 1);
        String reference = buy(hall, customer, hall.seat(1));

        mockMvc.perform(get("/api/bookings/" + reference).header("Authorization", adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.email").value(CUSTOMER_EMAIL));
    }

    @Test
    @DisplayName("booking history needs an account")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/bookings/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/bookings/LUM-ABCDEF")).andExpect(status().isUnauthorized());
    }

    private String buy(Auditorium hall, String bearer, long... seatIds) throws Exception {
        var result = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(hall.showingId(), seatIds)))
                .andExpect(status().isCreated())
                .andReturn();
        return asJson(result).get("reference").asText();
    }
}
