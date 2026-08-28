package com.cinema.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Typed view of the {@code app.*} block in application.yml.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(SeatHold seatHold, Jwt jwt, Tmdb tmdb) {

    /**
     * TECH.md §5 — how long a Redis seat hold survives without confirmation, and how many seats
     * one user may hold at once on a single showing.
     *
     * @param maxSeatsPerUser without a cap, one script can hold an entire auditorium for the
     *                        full TTL and lock every other customer out. Counted per showing, so
     *                        it does not stop someone booking several screenings.
     */
    public record SeatHold(int ttlSeconds, int maxSeatsPerUser) {

        /** The same value as the Redis {@code EX} argument wants it. */
        public Duration ttl() {
            return Duration.ofSeconds(ttlSeconds);
        }
    }

    public record Jwt(String issuer, int accessTokenTtlMinutes, int refreshTokenTtlDays, String secret) {
    }

    /**
     * Where the real movie catalogue comes from. Nothing calls TMDB unless an admin asks it to,
     * so an unset key is not a startup failure — it is a 503 on the one endpoint that needs it,
     * which keeps the app (and every test) runnable with no network at all.
     *
     * @param apiKey        either a v3 API key or a v4 read access token; {@link #bearerToken()}
     *                      tells them apart, because TMDB authenticates the two differently
     * @param imageBaseUrl  TMDB serves artwork from a separate CDN host to the JSON API
     * @param posterSize    the size segment of the image URL. w500 is the smallest width that
     *                      still looks right on a 240px card at 2x device pixel ratio
     */
    public record Tmdb(String apiKey, String baseUrl, String imageBaseUrl, String posterSize, String language,
                       String region) {

        public boolean configured() {
            return apiKey != null && !apiKey.isBlank();
        }

        /**
         * A v4 read access token is a JWT and goes in an Authorization header; a v3 key is 32 hex
         * characters and goes in the query string. TMDB's settings page offers both and does not
         * make the difference obvious, so the shape of the value decides rather than the user.
         */
        public boolean bearerToken() {
            return configured() && apiKey.chars().filter(c -> c == '.').count() == 2;
        }

        /** Turns TMDB's bare {@code /abc.jpg} poster path into a URL a browser can load. */
        public String posterUrl(String posterPath) {
            if (posterPath == null || posterPath.isBlank()) {
                return null;
            }
            return imageBaseUrl + posterSize + posterPath;
        }
    }
}
