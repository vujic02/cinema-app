package com.cinema.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Used by both {@code /refresh} and {@code /logout} — the same opaque token identifies the session. */
public record RefreshRequest(

        @NotBlank
        String refreshToken
) {
}
