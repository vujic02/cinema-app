package com.cinema.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of the {@code app.*} block in application.yml.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(SeatHold seatHold, Jwt jwt) {

    /** TECH.md §5 — how long a Redis seat hold survives without confirmation. */
    public record SeatHold(int ttlSeconds) {
    }

    public record Jwt(String issuer, int accessTokenTtlMinutes, int refreshTokenTtlDays, String secret) {
    }
}
