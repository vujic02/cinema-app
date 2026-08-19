package com.cinema.booking.web;

import com.cinema.booking.domain.BookingStatus;
import com.cinema.booking.dto.AnalyticsResponse;
import com.cinema.booking.dto.BookingResponse;
import com.cinema.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: bookings", description = "All bookings, with filters, plus platform analytics")
public class AdminBookingController {

    private final BookingService bookingService;

    @GetMapping("/bookings")
    @Operation(summary = "Every booking, filtered",
            description = """
                    Any combination of date range, venue, movie and status. The dates filter on
                    the showing's start time — an admin asking for a weekend means the
                    screenings, not the purchases. Both ends are inclusive.
                    """)
    public List<BookingResponse> find(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) BookingStatus status) {
        return bookingService.findForAdmin(from, to, venueId, movieId, status);
    }

    @GetMapping("/analytics")
    @Operation(summary = "Dashboard counters, top showings and revenue by date",
            description = "Revenue is keyed on the day of purchase. Without `since`, all time.")
    public AnalyticsResponse analytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since) {
        return bookingService.analytics(since);
    }
}
