package com.cinema.venue.repository;

/**
 * Projection interface (TECH.md §3a tier 4) so the venue list can show "5 rows / 29 seats"
 * without either an N+1 per venue or a cartesian fetch join across two collections.
 */
public interface VenueLayoutCounts {

    Long getVenueId();

    long getRowCount();

    long getSeatCount();
}
