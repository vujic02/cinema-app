package com.cinema.seat.domain;

import java.util.Optional;

/**
 * The Redis key a live seat hold lives under: {@code hold:{showingId}:{seatId}} (TECH.md §5).
 * <p>
 * Both ids are encoded in the key, not just the seat, because the expiry notification Redis
 * publishes carries <em>only the key name</em> — the value is already gone by then. Anything the
 * expiry listener needs in order to broadcast has to be readable straight off the key.
 */
public record HoldKey(long showingId, long seatId) {

    private static final String PREFIX = "hold:";

    public String format() {
        return PREFIX + showingId + ":" + seatId;
    }

    /** Matches every hold on one showing — the {@code SCAN} pattern behind the seat map. */
    public static String showingPattern(long showingId) {
        return PREFIX + showingId + ":*";
    }

    /**
     * Total by design: the expiry listener is handed every key that expires in the database,
     * not only ours, so anything unparseable has to be a quiet no-match rather than a throw.
     */
    public static Optional<HoldKey> parse(String key) {
        if (key == null || !key.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String[] parts = key.substring(PREFIX.length()).split(":");
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            return Optional.of(new HoldKey(Long.parseLong(parts[0]), Long.parseLong(parts[1])));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
