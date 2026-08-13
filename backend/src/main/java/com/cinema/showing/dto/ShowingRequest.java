package com.cinema.showing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code startTime} is an instant, not a local date-time: the database stores UTC and the client
 * sends ISO-8601. There is deliberately no {@code @Future} here — it would block editing the
 * price of a showing that has already happened. The service enforces it on create only.
 */
public record ShowingRequest(

        @NotNull
        Long movieId,

        @NotNull
        Long venueId,

        @NotNull
        Instant startTime,

        @NotNull
        @DecimalMin(value = "0.0", message = "Price cannot be negative")
        @Digits(integer = 8, fraction = 2)
        BigDecimal price
) {
}
