package com.cinema.booking.domain;

/**
 * TECH.md §6. In practice v1 only ever writes {@link #SOLD} rows: live holds are Redis
 * keys with a TTL (§5), not database rows, so an expiring hold costs no write. HELD
 * exists so a hold can be persisted if Redis is ever taken out of the path.
 */
public enum BookingStatus {
    HELD,
    SOLD
}
