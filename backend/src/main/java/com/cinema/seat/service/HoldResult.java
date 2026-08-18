package com.cinema.seat.service;

import java.time.Duration;

/**
 * Outcome of one attempt at {@code SET hold:… NX EX}, plus how long the winning hold has left.
 *
 * @param remaining time left on the hold — the full TTL when it was just acquired, whatever is
 *                  left on the existing key otherwise
 */
public record HoldResult(Outcome outcome, Duration remaining) {

    public enum Outcome {
        /** The {@code NX} write landed: this caller now owns the seat. */
        ACQUIRED,
        /**
         * The key was already there and already this caller's. Treated as success and
         * <em>not</em> refreshed — extending the TTL on every click would let one user sit on a
         * seat indefinitely by re-clicking it.
         */
        ALREADY_YOURS,
        /** Someone else got there first. The loser of the race described in TECH.md §5. */
        HELD_BY_OTHER
    }
}
