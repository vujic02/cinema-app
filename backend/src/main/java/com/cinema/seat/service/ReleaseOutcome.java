package com.cinema.seat.service;

public enum ReleaseOutcome {
    /** The caller's own hold was deleted. */
    RELEASED,
    /**
     * Nothing to delete — the hold had already lapsed, or was never placed. Not an error: the
     * frontend releases on navigate-away, which routinely happens after the TTL has fired.
     */
    NOT_HELD,
    /** The key exists but belongs to another user, so it is left alone. */
    NOT_YOURS
}
