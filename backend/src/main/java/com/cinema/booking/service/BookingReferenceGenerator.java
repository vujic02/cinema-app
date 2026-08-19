package com.cinema.booking.service;

import com.cinema.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Mints the {@code LUM-…} reference a customer reads off their ticket.
 *
 * <p>Server-side, because the handoff generated it in the browser: two clients could mint the
 * same string, and a reference nobody had recorded could not be looked up afterwards.
 *
 * <p><b>Why not a database constraint.</b> The reference is shared by every row of one purchase,
 * so {@code UNIQUE (booking_reference)} would forbid buying two seats at once. Uniqueness is
 * therefore established here, by generating from a space large enough that collisions are
 * vanishingly rare and then checking anyway.
 */
@Component
@RequiredArgsConstructor
public class BookingReferenceGenerator {

    /**
     * Crockford-style: no I, L, O, U, and no 0 or 1. A reference gets read aloud over a counter
     * and typed off a phone screen, so the characters people confuse are simply absent.
     */
    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int LENGTH = 6;
    private static final String PREFIX = "LUM-";

    /**
     * 30^6 is about 729 million. Even at a million bookings the chance of any collision at all is
     * under a percent, and the loop below catches those — but not an unbounded number of times,
     * because a generator that cannot find a free reference in five tries is broken, not unlucky.
     */
    private static final int MAX_ATTEMPTS = 5;

    private final SecureRandom random = new SecureRandom();
    private final BookingRepository bookings;

    public String next() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = mint();
            if (!bookings.existsByBookingReference(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Could not mint an unused booking reference in " + MAX_ATTEMPTS + " attempts");
    }

    private String mint() {
        StringBuilder reference = new StringBuilder(PREFIX);
        for (int i = 0; i < LENGTH; i++) {
            reference.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return reference.toString();
    }
}
