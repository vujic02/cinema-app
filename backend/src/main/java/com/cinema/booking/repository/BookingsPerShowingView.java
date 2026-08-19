package com.cinema.booking.repository;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Projection interface (TECH.md §3a tier 4) for the admin dashboard's per-showing table: how
 * full each screening is and what it took.
 * <p>
 * A projection rather than entities because nothing here is ever written back, and hydrating a
 * Booking per ticket to add up two numbers would be a lot of objects to throw away.
 */
public interface BookingsPerShowingView {

    Long getShowingId();

    String getMovieTitle();

    String getVenueName();

    Instant getStartTime();

    long getSeatsSold();

    BigDecimal getRevenue();
}
