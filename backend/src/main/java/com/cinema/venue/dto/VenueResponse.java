package com.cinema.venue.dto;

import com.cinema.venue.domain.Venue;

/**
 * @param rowCount  number of rows in the venue's layout, 0 before one is generated
 * @param seatCount total seats across those rows
 */
public record VenueResponse(Long id, String name, String address, long rowCount, long seatCount) {

    public static VenueResponse of(Venue venue, long rowCount, long seatCount) {
        return new VenueResponse(venue.getId(), venue.getName(), venue.getAddress(), rowCount, seatCount);
    }
}
