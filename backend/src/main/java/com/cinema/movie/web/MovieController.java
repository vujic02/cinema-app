package com.cinema.movie.web;

import com.cinema.movie.dto.MovieResponse;
import com.cinema.movie.service.MovieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Movies", description = "Public movie reads")
public class MovieController {

    private final MovieService movieService;

    @GetMapping
    @Operation(summary = "All movies", description = "Optionally filtered by a title fragment.")
    public List<MovieResponse> findAll(@RequestParam(required = false) String q) {
        return movieService.findAll(q);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One movie")
    public MovieResponse findById(@PathVariable Long id) {
        return movieService.findById(id);
    }
}
