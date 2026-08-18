package com.cinema.seat.dto;

import com.cinema.seat.domain.SeatStatus;

/**
 * What goes out on {@code /topic/showings/{id}} — TECH.md §5's {@code { seatId, status }}, plus
 * the showing id so a client subscribed to several topics can route without inspecting the
 * destination.
 * <p>
 * No user id, ever. The topic is readable by anyone watching the showing, so "who" is not
 * broadcastable; a subscriber only learns that a seat changed hands, never to whom.
 */
public record SeatStatusEvent(Long showingId, Long seatId, SeatStatus status) {
}
