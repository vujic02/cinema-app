package com.cinema.seat.dto;

import com.cinema.seat.domain.SeatStatus;

import java.time.Instant;

/**
 * The answer to a successful hold. Carries both a duration and an instant on purpose: the
 * countdown ticks off {@code expiresInSeconds} without trusting the browser clock, while
 * {@code expiresAt} survives a tab being backgrounded and re-rendered.
 */
public record SeatHoldResponse(
        Long showingId,
        Long seatId,
        String label,
        SeatStatus status,
        long expiresInSeconds,
        Instant expiresAt
) {
}
