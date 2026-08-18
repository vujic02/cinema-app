package com.cinema.seat.web;

import com.cinema.auth.security.UserPrincipal;
import com.cinema.seat.dto.SeatHoldResponse;
import com.cinema.seat.dto.SeatMapResponse;
import com.cinema.seat.service.SeatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Seat map and seat holds, under the showing they belong to.
 *
 * <p>Only the GET is public: {@code SecurityConfig} permits {@code GET /api/showings/**} for
 * catalogue browsing, which leaves the hold and release verbs requiring a token.
 */
@RestController
@RequestMapping("/api/showings/{showingId}")
@RequiredArgsConstructor
@Tag(name = "Seats", description = "Seat map and Redis-backed seat holds")
public class SeatController {

    private final SeatService seatService;

    @GetMapping("/seat-map")
    @Operation(summary = "Every seat of a showing with its live status",
            description = """
                    Sold seats come from MySQL, held seats from Redis. Readable anonymously; with
                    a token, seats the caller is holding are additionally flagged heldByYou.
                    """)
    public SeatMapResponse seatMap(@PathVariable Long showingId,
                                   @AuthenticationPrincipal UserPrincipal viewer) {
        return seatService.seatMap(showingId, viewer == null ? null : viewer.userId());
    }

    @PostMapping("/seats/{seatId}/hold")
    @Operation(summary = "Hold a seat for this user",
            description = """
                    Places a Redis key with a TTL. 409 SEAT_HELD if another user got there first,
                    409 SEAT_SOLD if it is already booked. Re-holding a seat you already hold
                    succeeds and returns the time left, without extending it.
                    """)
    public SeatHoldResponse hold(@PathVariable Long showingId,
                                 @PathVariable Long seatId,
                                 @AuthenticationPrincipal UserPrincipal principal) {
        return seatService.hold(showingId, seatId, principal.userId());
    }

    @DeleteMapping("/seats/{seatId}/hold")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Give a held seat back",
            description = """
                    Idempotent: releasing a hold that has already expired is a no-op, not an
                    error. 409 HOLD_NOT_YOURS if the seat is held by somebody else.
                    """)
    public void release(@PathVariable Long showingId,
                        @PathVariable Long seatId,
                        @AuthenticationPrincipal UserPrincipal principal) {
        seatService.release(showingId, seatId, principal.userId());
    }
}
