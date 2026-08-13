package com.cinema.showing.web;

import com.cinema.showing.dto.ShowingRequest;
import com.cinema.showing.dto.ShowingResponse;
import com.cinema.showing.service.ShowingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/showings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: showings", description = "Schedule CRUD with dynamic filters")
public class AdminShowingController {

    private final ShowingService showingService;

    @GetMapping
    @Operation(summary = "Filter the schedule",
            description = "Any combination of date range, venue and movie. Both dates are inclusive.")
    public List<ShowingResponse> find(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) Long movieId) {
        return showingService.findForAdmin(from, to, venueId, movieId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Schedule a showing")
    public ShowingResponse create(@Valid @RequestBody ShowingRequest request) {
        return showingService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Reschedule or reprice a showing")
    public ShowingResponse update(@PathVariable Long id, @Valid @RequestBody ShowingRequest request) {
        return showingService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a showing", description = "Rejected once any booking exists against it.")
    public void delete(@PathVariable Long id) {
        showingService.delete(id);
    }
}
