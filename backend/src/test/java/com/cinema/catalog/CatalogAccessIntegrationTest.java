package com.cinema.catalog;

import com.cinema.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may reach the catalogue. Reads are open to everyone; every write is admin-only.
 * <p>
 * The three-way split matters and is easy to get wrong: anonymous must be <b>401</b> so the
 * frontend knows to refresh, while a logged-in customer must be <b>403</b> so it knows not to.
 */
class CatalogAccessIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("catalogue reads need no account at all")
    void readsArePublic() throws Exception {
        mockMvc.perform(get("/api/movies")).andExpect(status().isOk());
        mockMvc.perform(get("/api/venues")).andExpect(status().isOk());
        mockMvc.perform(get("/api/showings")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("anonymous writes are 401")
    void anonymousWritesAreUnauthorized() throws Exception {
        for (MockHttpServletRequestBuilder write : writeRequests()) {
            mockMvc.perform(write)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Test
    @DisplayName("a logged-in customer gets 403 on every admin write")
    void customerWritesAreForbidden() throws Exception {
        String customer = customerBearer();

        for (MockHttpServletRequestBuilder write : writeRequests()) {
            mockMvc.perform(write.header("Authorization", customer))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    @Test
    @DisplayName("a customer cannot read the admin schedule either")
    void customerCannotReadAdminSchedule() throws Exception {
        mockMvc.perform(get("/api/admin/showings").header("Authorization", customerBearer()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/showings").header("Authorization", adminBearer()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an expired-looking or forged token is 401, never 403")
    void forgedTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/showings").header("Authorization", "Bearer forged.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    /** One representative write per admin resource, bodies valid so only authorisation can fail. */
    private MockHttpServletRequestBuilder[] writeRequests() {
        return new MockHttpServletRequestBuilder[]{
                post("/api/admin/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Should Not Exist","address":"nowhere"}
                                """),
                put("/api/admin/venues/1/layout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rows":[{"seatCount":4}]}
                                """),
                delete("/api/admin/venues/1"),
                post("/api/admin/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Should Not Exist","description":"","durationMinutes":100,
                                 "genre":"Drama","rating":"PG","posterHue":10}
                                """),
                delete("/api/admin/movies/1"),
                post("/api/admin/showings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"movieId":1,"venueId":1,"startTime":"2099-01-01T18:00:00Z","price":10.00}
                                """),
                delete("/api/admin/showings/1")
        };
    }
}
