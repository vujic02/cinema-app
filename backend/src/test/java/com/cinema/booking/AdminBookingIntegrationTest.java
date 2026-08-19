package com.cinema.booking;

import com.cinema.seat.AbstractSeatIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/admin/bookings} and {@code /api/admin/analytics}.
 * <p>
 * Every assertion is scoped to a venue this test creates, so the seeded sales — and whatever the
 * other classes sharing the container have written — cannot move the numbers.
 */
class AdminBookingIntegrationTest extends AbstractSeatIntegrationTest {

    @Test
    @DisplayName("the venue filter returns that venue's sales, folded one row per purchase")
    void filtersByVenue() throws Exception {
        Auditorium hall = auditorium();
        String customer = customerBearer();
        hold(hall, customer, 0, 1);
        buy(hall, customer, hall.seat(0), hall.seat(1));

        mockMvc.perform(get("/api/admin/bookings")
                        .header("Authorization", adminBearer())
                        .param("venueId", String.valueOf(hall.venueId())))
                .andExpect(status().isOk())
                // Two seats, one purchase, one row in the table.
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].seats.length()").value(2))
                .andExpect(jsonPath("$[0].total").value(24.00))
                .andExpect(jsonPath("$[0].status").value("SOLD"))
                // The admin view names the buyer; the customer's own view does not.
                .andExpect(jsonPath("$[0].customer.email").value(CUSTOMER_EMAIL));
    }

    @Test
    @DisplayName("two customers buying into one showing are two rows")
    void separatesPurchases() throws Exception {
        Auditorium hall = auditorium();
        String customer = customerBearer();
        String sam = bearerFor("sam@lumen.test", "password123");

        hold(hall, customer, 0);
        buy(hall, customer, hall.seat(0));
        hold(hall, sam, 1);
        buy(hall, sam, hall.seat(1));

        mockMvc.perform(get("/api/admin/bookings")
                        .header("Authorization", adminBearer())
                        .param("venueId", String.valueOf(hall.venueId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("the date range filters on the showing's day, both ends inclusive")
    void filtersByShowingDateRange() throws Exception {
        Auditorium hall = auditorium();
        String customer = customerBearer();
        hold(hall, customer, 0);
        buy(hall, customer, hall.seat(0));

        LocalDate showingDay = showings.findById(hall.showingId()).orElseThrow()
                .getStartTime().atZone(ZoneOffset.UTC).toLocalDate();

        mockMvc.perform(get("/api/admin/bookings")
                        .header("Authorization", adminBearer())
                        .param("venueId", String.valueOf(hall.venueId()))
                        .param("from", showingDay.toString())
                        .param("to", showingDay.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // A window that ends the day before the screening excludes it.
        mockMvc.perform(get("/api/admin/bookings")
                        .header("Authorization", adminBearer())
                        .param("venueId", String.valueOf(hall.venueId()))
                        .param("to", showingDay.minusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("the status filter narrows to SOLD, and HELD matches nothing")
    void filtersByStatus() throws Exception {
        Auditorium hall = auditorium();
        String customer = customerBearer();
        hold(hall, customer, 0);
        buy(hall, customer, hall.seat(0));

        mockMvc.perform(get("/api/admin/bookings")
                        .header("Authorization", adminBearer())
                        .param("venueId", String.valueOf(hall.venueId()))
                        .param("status", "SOLD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Live holds are Redis keys, never bookings rows, so HELD is empty by design.
        mockMvc.perform(get("/api/admin/bookings")
                        .header("Authorization", adminBearer())
                        .param("venueId", String.valueOf(hall.venueId()))
                        .param("status", "HELD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("analytics totals and both series reflect a sale")
    void reportsAnalytics() throws Exception {
        Auditorium hall = auditorium();
        String customer = customerBearer();
        hold(hall, customer, 0, 1, 2);
        buy(hall, customer, hall.seat(0), hall.seat(1), hall.seat(2));

        String admin = adminBearer();
        var before = asJson(mockMvc.perform(get("/api/admin/analytics").header("Authorization", admin))
                .andExpect(status().isOk())
                .andReturn());

        // Counters are platform-wide, so assert they are sane and include this sale rather than
        // pinning an absolute number the seed data would break.
        long tickets = before.get("totals").get("tickets").asLong();
        long purchases = before.get("totals").get("purchases").asLong();
        assertThatAtLeast(tickets, 3);
        assertThatAtLeast(purchases, 1);
        assertThatAtLeast(before.get("totals").get("revenue").asLong(), 36);

        // This showing appears in the per-showing series with its own three seats and $36.
        mockMvc.perform(get("/api/admin/analytics").header("Authorization", admin))
                .andExpect(jsonPath("$.topShowings[?(@.showingId == " + hall.showingId() + ")].seatsSold")
                        .value(3))
                .andExpect(jsonPath("$.topShowings[?(@.showingId == " + hall.showingId() + ")].revenue")
                        .value(36.00))
                // Purchases happen today, so the revenue series has at least one day in it.
                .andExpect(jsonPath("$.revenueByDate.length()")
                        .value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.revenueByDate[0].date").isNotEmpty());
    }

    @Test
    @DisplayName("both admin endpoints are closed to customers and to anonymous callers")
    void requiresAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/bookings")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/analytics")).andExpect(status().isUnauthorized());

        String customer = customerBearer();
        mockMvc.perform(get("/api/admin/bookings").header("Authorization", customer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/analytics").header("Authorization", customer))
                .andExpect(status().isForbidden());
    }

    private static void assertThatAtLeast(long actual, long floor) {
        org.assertj.core.api.Assertions.assertThat(actual).isGreaterThanOrEqualTo(floor);
    }

    private void buy(Auditorium hall, String bearer, long... seatIds) throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(hall.showingId(), seatIds)))
                .andExpect(status().isCreated());
    }
}
