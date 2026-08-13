package com.cinema.auth.service;

import com.cinema.auth.domain.RefreshToken;
import com.cinema.auth.domain.User;
import com.cinema.auth.repository.RefreshTokenRepository;
import com.cinema.common.exception.UnauthorizedException;
import com.cinema.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Refresh tokens are opaque random strings, not JWTs.
 * <p>
 * {@code refresh_tokens.token_hash} is looked up directly
 * ({@link RefreshTokenRepository#findByTokenHashAndRevokedFalse}), which rules out a salted hash
 * like BCrypt — the same input has to produce the same 64 hex characters every time. SHA-256 is
 * appropriate here precisely because the token is 256 bits of entropy from a CSPRNG: there is no
 * low-entropy secret to brute-force, unlike a password.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final RefreshTokenRepository refreshTokens;
    private final AppProperties properties;

    /** Returns the raw token. It is handed to the client here and never recoverable again. */
    @Transactional
    public String issue(User user) {
        byte[] raw = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        refreshTokens.save(RefreshToken.builder()
                .user(user)
                .tokenHash(hash(token))
                .expiresAt(Instant.now().plus(properties.jwt().refreshTokenTtlDays(), ChronoUnit.DAYS))
                .revoked(false)
                .build());

        return token;
    }

    /**
     * @throws UnauthorizedException if the token is unknown, already revoked, or past its expiry
     */
    @Transactional(readOnly = true)
    public RefreshToken verify(String rawToken) {
        RefreshToken stored = refreshTokens.findByTokenHashAndRevokedFalse(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException(
                        "REFRESH_TOKEN_INVALID", "Refresh token is not valid"));

        // The repository query filters on `revoked` only — expiry is deliberately not part of
        // the derived query, so it has to be checked here.
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("REFRESH_TOKEN_EXPIRED", "Refresh token has expired");
        }
        return stored;
    }

    @Transactional
    public void revoke(RefreshToken token) {
        token.setRevoked(true);
        refreshTokens.save(token);
    }

    /** SHA-256 hex — exactly the 64 characters {@code refresh_tokens.token_hash} holds. */
    public static String hash(String rawToken) {
        try {
            // MessageDigest is not thread-safe, so never cache the instance.
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandated by every JVM", e);
        }
    }
}
