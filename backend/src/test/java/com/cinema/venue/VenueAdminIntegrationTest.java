package com.cinema.venue;

import com.cinema.support.AbstractIntegrationTest;
import com.cinema.venue.repository.VenueRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Venue CRUD and the seat-layout generator, end to end through the API. */
class VenueAdminIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private VenueRepository venues;

    @Test
    @DisplayName("a new venue starts with no layout")
    void createsVenue() throws Exception {
        String name = uniqueName();

        mockMvc.perform(post("/api/admin/venues")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(venueBody(name, "1 Test Street")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.rowCount").value(0))
                .andExpect(jsonPath("$.seatCount").value(0));
    }

    @Test
    @DisplayName("venue names are unique")
    void rejectsDuplicateVenueName() throws Exception {
        String name = uniqueName();
        createVenue(name);

        mockMvc.perform(post("/api/admin/venues")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(venueBody(name, "2 Test Street")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VENUE_NAME_TAKEN"));
    }

    @Test
    @DisplayName("renaming a venue to its own name is allowed")
    void updatesVenue() throws Exception {
        String name = uniqueName();
        long id = createVenue(name);

        mockMvc.perform(put("/api/admin/venues/" + id)
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(venueBody(name, "99 Renamed Road")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("99 Renamed Road"));
    }

    @Test
    @DisplayName("generating a layout produces the TECH.md §6 reference plan")
    void generatesReferenceLayout() throws Exception {
        long id = createVenue(uniqueName());

        mockMvc.perform(put("/api/admin/venues/" + id + "/layout")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rows":[
                                  {"seatCount":4},
                                  {"seatCount":6},
                                  {"seatCount":6},
                                  {"seatCount":6},
                                  {"seatCount":7}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatCount").value(29))
                .andExpect(jsonPath("$.rows.length()").value(5))
                .andExpect(jsonPath("$.rows[0].rowLabel").value("A"))
                .andExpect(jsonPath("$.rows[4].rowLabel").value("E"))
                .andExpect(jsonPath("$.rows[4].seats.length()").value(7))
                .andExpect(jsonPath("$.rows[2].seats[3].label").value("C4"));

        // And the public read agrees.
        mockMvc.perform(get("/api/venues/" + id + "/layout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatCount").value(29));

        mockMvc.perform(get("/api/venues/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowCount").value(5))
                .andExpect(jsonPath("$.seatCount").value(29));
    }

    @Test
    @DisplayName("aisle gaps land on the seat they follow")
    void generatesAisleGaps() throws Exception {
        long id = createVenue(uniqueName());

        mockMvc.perform(put("/api/admin/venues/" + id + "/layout")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rows":[{"seatCount":6,"aisleGapsAfter":[2,4],"seatType":"PREMIUM"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].seats[1].aisleGap").value(true))
                .andExpect(jsonPath("$.rows[0].seats[3].aisleGap").value(true))
                .andExpect(jsonPath("$.rows[0].seats[0].aisleGap").value(false))
                .andExpect(jsonPath("$.rows[0].seats[0].seatType").value("PREMIUM"));
    }

    /**
     * The replacement reuses row labels A and B, which are still occupied by the old rows when
     * the new ones are built — the case that fails if the deletes are not flushed first.
     */
    @Test
    @DisplayName("a layout can be replaced with one that reuses the same row labels")
    void replacesLayoutReusingLabels() throws Exception {
        long id = createVenue(uniqueName());

        putLayout(id, """
                {"rows":[{"seatCount":5},{"seatCount":5},{"seatCount":5}]}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.seatCount").value(15));

        putLayout(id, """
                {"rows":[{"seatCount":8},{"seatCount":8}]}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.seatCount").value(16))
                .andExpect(jsonPath("$.rows[0].rowLabel").value("A"))
                .andExpect(jsonPath("$.rows[1].rowLabel").value("B"));
    }

    @Test
    @DisplayName("an aisle gap past the end of its row is a 400")
    void rejectsImpossibleAisleGap() throws Exception {
        long id = createVenue(uniqueName());

        putLayout(id, """
                {"rows":[{"seatCount":4,"aisleGapsAfter":[9]}]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AISLE_GAP"));
    }

    @Test
    @DisplayName("duplicate row labels are a 400")
    void rejectsDuplicateRowLabels() throws Exception {
        long id = createVenue(uniqueName());

        putLayout(id, """
                {"rows":[{"label":"A","seatCount":4},{"label":"A","seatCount":4}]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ROW_LABEL"));
    }

    @Test
    @DisplayName("bean validation catches an empty spec and an over-wide row")
    void rejectsInvalidLayoutShape() throws Exception {
        long id = createVenue(uniqueName());

        putLayout(id, """
                {"rows":[]}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        putLayout(id, """
                {"rows":[{"seatCount":41}]}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("a venue with bookings against it can no longer be re-laid-out")
    void rejectsLayoutChangeOnceBookingsExist() throws Exception {
        // Downtown 8 carries the seeded LUM-40218 sale.
        long id = venues.findByNameIgnoreCase("Downtown 8").orElseThrow().getId();

        putLayout(id, """
                {"rows":[{"seatCount":4}]}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAYOUT_LOCKED"));
    }

    @Test
    @DisplayName("a venue that still has showings cannot be deleted")
    void rejectsDeletingVenueWithShowings() throws Exception {
        long id = venues.findByNameIgnoreCase("Uptown Cineplex").orElseThrow().getId();

        mockMvc.perform(delete("/api/admin/venues/" + id).header("Authorization", adminBearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VENUE_HAS_SHOWINGS"));
    }

    @Test
    @DisplayName("an unused venue deletes, taking its layout with it")
    void deletesUnusedVenue() throws Exception {
        long id = createVenue(uniqueName());
        putLayout(id, """
                {"rows":[{"seatCount":4}]}
                """).andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/venues/" + id).header("Authorization", adminBearer()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/venues/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ----------------------------------------------------------------- helpers

    private String uniqueName() {
        return "Test Venue " + UUID.randomUUID();
    }

    private String venueBody(String name, String address) {
        return """
                {"name":"%s","address":"%s"}
                """.formatted(name, address);
    }

    private long createVenue(String name) throws Exception {
        var result = mockMvc.perform(post("/api/admin/venues")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(venueBody(name, "1 Test Street")))
                .andExpect(status().isCreated())
                .andReturn();
        return asJson(result).get("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions putLayout(long venueId, String body) throws Exception {
        return mockMvc.perform(put("/api/admin/venues/" + venueId + "/layout")
                .header("Authorization", adminBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
