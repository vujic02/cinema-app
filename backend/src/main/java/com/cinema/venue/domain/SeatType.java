package com.cinema.venue.domain;

/**
 * Display label only in v1 — TECH.md §6 defines no per-type price multiplier, so
 * every seat in a showing costs {@code showings.price}.
 */
public enum SeatType {
    STANDARD,
    PREMIUM,
    ACCESSIBLE
}
