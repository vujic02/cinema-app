package com.cinema.auth.dto;

/**
 * @param accessToken  short-lived JWT, sent as {@code Authorization: Bearer …}
 * @param refreshToken opaque random string — returned exactly once, only its SHA-256 is stored
 * @param expiresIn    access token lifetime in seconds, so the client can refresh pre-emptively
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
    public static AuthResponse of(String accessToken, String refreshToken, long expiresIn, UserResponse user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
