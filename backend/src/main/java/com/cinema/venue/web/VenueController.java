package com.cinema.venue.web;

import com.cinema.venue.dto.VenueLayoutResponse;
import com.cinema.venue.dto.VenueResponse;
import com.cinema.venue.service.VenueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public reads. Browsing the catalogue never requires an account. */
@RestController
@RequestMapping("/api/venues")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Venues", description = "Public venue reads")
public class VenueController {

    private final VenueService venueService;

    @GetMapping
    @Operation(summary = "All venues, for the showings filter")
    public List<VenueResponse> findAll() {
        return venueService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "One venue")
    public VenueResponse findById(@PathVariable Long id) {
        return venueService.findById(id);
    }

    @GetMapping("/{id}/layout")
    @Operation(summary = "The venue's seating plan",
            description = "Seats and aisle gaps only. Per-showing availability is the seat-map endpoint.")
    public VenueLayoutResponse findLayout(@PathVariable Long id) {
        return venueService.findLayout(id);
    }
}
