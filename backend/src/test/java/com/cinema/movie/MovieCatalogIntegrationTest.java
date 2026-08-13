package com.cinema.movie;

import com.cinema.movie.repository.MovieRepository;
import com.cinema.support.AbstractIntegrationTest;
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

class MovieCatalogIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MovieRepository movies;

    @Test
    @DisplayName("a movie round-trips through create, read and update")
    void createsAndUpdatesMovie() throws Exception {
        String title = "Test Film " + UUID.randomUUID();

        var created = mockMvc.perform(post("/api/admin/movies")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movieBody(title, 142, "Thriller", "PG-13", 260)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.durationMinutes").value(142))
                .andExpect(jsonPath("$.posterHue").value(260))
                .andReturn();

        long id = asJson(created).get("id").asLong();

        mockMvc.perform(get("/api/movies/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.genre").value("Thriller"));

        mockMvc.perform(put("/api/admin/movies/" + id)
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movieBody(title, 99, "Comedy", "PG", 10)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.genre").value("Comedy"))
                .andExpect(jsonPath("$.durationMinutes").value(99));
    }

    @Test
    @DisplayName("the public list is searchable by title fragment")
    void searchesByTitle() throws Exception {
        String marker = "Zqx" + UUID.randomUUID().toString().substring(0, 8);
        createMovie(marker + " The Return");

        mockMvc.perform(get("/api/movies").param("q", marker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value(marker + " The Return"));

        mockMvc.perform(get("/api/movies"))
                .andExpect(status().isOk())
                // The seed alone provides five, so an unfiltered list is never empty.
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(5)));
    }

    @Test
    @DisplayName("out-of-range duration and poster hue are field errors, not database errors")
    void validatesMovieRequest() throws Exception {
        mockMvc.perform(post("/api/admin/movies")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movieBody("", 0, "Drama", "PG", 400)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.title").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.durationMinutes").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.posterHue").isNotEmpty());
    }

    @Test
    @DisplayName("an unscheduled movie deletes; one with showings does not")
    void guardsDeletion() throws Exception {
        long unscheduled = createMovie("Unscheduled " + UUID.randomUUID());

        mockMvc.perform(delete("/api/admin/movies/" + unscheduled).header("Authorization", adminBearer()))
                .andExpect(status().isNoContent());

        // Every seeded movie is on the schedule.
        long scheduled = movies.findAllByOrderByTitleAsc().getFirst().getId();
        mockMvc.perform(delete("/api/admin/movies/" + scheduled).header("Authorization", adminBearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MOVIE_HAS_SHOWINGS"));
    }

    @Test
    @DisplayName("an unknown movie is a 404 with the standard error shape")
    void unknownMovieIsNotFound() throws Exception {
        mockMvc.perform(get("/api/movies/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/movies/999999"));
    }

    private String movieBody(String title, int duration, String genre, String rating, int hue) {
        return """
                {"title":"%s","description":"A test film.","durationMinutes":%d,
                 "genre":"%s","rating":"%s","posterHue":%d}
                """.formatted(title, duration, genre, rating, hue);
    }

    private long createMovie(String title) throws Exception {
        var result = mockMvc.perform(post("/api/admin/movies")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movieBody(title, 100, "Drama", "PG", 120)))
                .andExpect(status().isCreated())
                .andReturn();
        return asJson(result).get("id").asLong();
    }
}
