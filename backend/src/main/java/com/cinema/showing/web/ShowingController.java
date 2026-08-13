package com.cinema.showing.web;

import com.cinema.showing.dto.ShowingResponse;
import com.cinema.showing.service.ShowingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/showings")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Showings", description = "Public showing reads")
public class ShowingController {

    private final ShowingService showingService;

    @GetMapping
    @Operation(summary = "Showings, optionally filtered",
            description = "Without a date, returns everything still upcoming. Dates are UTC (yyyy-MM-dd).")
    public List<ShowingResponse> find(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) Long movieId) {
        return showingService.findPublic(date, venueId, movieId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One showing")
    public ShowingResponse findById(@PathVariable Long id) {
        return showingService.findById(id);
    }
}
