package com.cinema.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A checkout: one showing, every seat the customer is holding for it.
 * <p>
 * No price and no total — the client does not get to say what a ticket costs. The server reads
 * {@code showings.price} and snapshots it onto every row (TECH.md §6).
 *
 * @param seatIds the seats to buy. Duplicates are collapsed rather than rejected, so a
 *                double-click that sends the same seat twice buys one ticket, not two.
 */
public record BookingRequest(

        @NotNull
        Long showingId,

        @NotEmpty
        @Size(max = 20, message = "A single purchase cannot exceed 20 seats")
        List<@NotNull Long> seatIds
) {
}
