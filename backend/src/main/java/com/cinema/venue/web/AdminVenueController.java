package com.cinema.venue.web;

import com.cinema.venue.dto.VenueLayoutRequest;
import com.cinema.venue.dto.VenueLayoutResponse;
import com.cinema.venue.dto.VenueRequest;
import com.cinema.venue.dto.VenueResponse;
import com.cinema.venue.service.VenueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/venues")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: venues", description = "Venue CRUD and seat-layout generation")
public class AdminVenueController {

    private final VenueService venueService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a venue", description = "Created without a layout; generate one separately.")
    public VenueResponse create(@Valid @RequestBody VenueRequest request) {
        return venueService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename or re-address a venue")
    public VenueResponse update(@PathVariable Long id, @Valid @RequestBody VenueRequest request) {
        return venueService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a venue", description = "Rejected while showings are scheduled in it.")
    public void delete(@PathVariable Long id) {
        venueService.delete(id);
    }

    @PutMapping("/{id}/layout")
    @Operation(summary = "Generate the seat layout from a row-spec",
            description = "Replaces the whole plan. Rejected once bookings exist against the venue.")
    public VenueLayoutResponse replaceLayout(@PathVariable Long id,
                                             @Valid @RequestBody VenueLayoutRequest request) {
        return venueService.replaceLayout(id, request);
    }
}
