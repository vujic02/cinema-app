package com.cinema.auth.service;

import com.cinema.auth.domain.User;
import com.cinema.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and verifies access tokens.
 * <p>
 * Uses the jjwt 0.12 API ({@code subject()}, {@code parser().verifyWith(…)}). The 0.11 API
 * ({@code setSubject()}, {@code parserBuilder()}) that most examples still show does not compile
 * against the 0.12.6 on this classpath.
 */
@Service
public class JwtService {

    private final AppProperties.Jwt config;
    private final SecretKey signingKey;

    public JwtService(AppProperties properties) {
        this.config = properties.jwt();
        // Throws WeakKeyException for anything under 256 bits, so a too-short secret fails at
        // startup rather than on the first login attempt.
        this.signingKey = Keys.hmacShaKeyFor(config.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Subject is the user id — immutable, unlike the email, and it is what the seat-hold keys
     * in Part 4 need. Role travels as a claim so authorisation costs no query; the 15 minute
     * TTL bounds how long a role change can stay stale.
     */
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(config.issuer())
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl())))
                .signWith(signingKey)
                .compact();
    }

    /** Throws {@link io.jsonwebtoken.JwtException} for a bad signature, bad issuer or expiry. */
    public Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(config.issuer())
                .build()
                .parseSignedClaims(token);
    }

    public Duration accessTokenTtl() {
        return Duration.ofMinutes(config.accessTokenTtlMinutes());
    }
}
