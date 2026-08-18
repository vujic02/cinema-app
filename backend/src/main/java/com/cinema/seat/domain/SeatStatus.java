package com.cinema.seat.domain;

/**
 * What a seat looks like to the room, per TECH.md §5. Deliberately has no "held by me" member:
 * the WebSocket topic is shared by everyone watching a showing, so the broadcast must not carry
 * anything user-specific. Whether a hold is the caller's own is a field on the authenticated
 * seat-map read instead ({@code heldByYou}), never part of a broadcast.
 */
public enum SeatStatus {
    AVAILABLE,
    HELD,
    SOLD
}
