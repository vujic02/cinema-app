package com.cinema.auth.security;

import com.cinema.auth.domain.Role;

/**
 * What the JWT filter puts in the SecurityContext, built entirely from token claims — no
 * database round trip per request.
 * <p>
 * Carries {@code userId} rather than just the email because Parts 4 and 5 key Redis seat holds
 * and booking ownership off the user id.
 */
public record UserPrincipal(Long userId, String email, Role role) {

    public String authority() {
        return "ROLE_" + role.name();
    }
}
