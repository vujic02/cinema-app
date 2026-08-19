package com.cinema.booking.web;

import com.cinema.auth.security.UserPrincipal;
import com.cinema.booking.dto.BookingRequest;
import com.cinema.booking.dto.BookingResponse;
import com.cinema.booking.dto.MyBookingsResponse;
import com.cinema.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checkout and the customer's own booking history. Every route here requires a token — nothing
 * under {@code /api/bookings} is in the public matchers of {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Simulated checkout and booking history")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Buy the seats this user is holding",
            description = """
                    Simulated payment (TECH.md §Payments): confirming turns held seats into sold
                    ones, releases the Redis holds and broadcasts SOLD. 409 HOLD_EXPIRED if a
                    hold lapsed, 409 SEAT_HELD if another user now holds one, 409 SEAT_SOLD if it
                    has already been bought.
                    """)
    public BookingResponse confirm(@Valid @RequestBody BookingRequest request,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return bookingService.confirm(request, principal.userId());
    }

    @GetMapping("/me")
    @Operation(summary = "This user's bookings, split into upcoming and past")
    public MyBookingsResponse myBookings(@AuthenticationPrincipal UserPrincipal principal) {
        return bookingService.findMine(principal.userId());
    }

    @GetMapping("/{reference}")
    @Operation(summary = "One purchase by its printed reference",
            description = "Owner or admin only. Someone else's reference answers 404, not 403.")
    public BookingResponse findByReference(@PathVariable String reference,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return bookingService.findByReference(reference, principal);
    }
}
