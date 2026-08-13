package com.cinema.auth.service;

import com.cinema.auth.domain.RefreshToken;
import com.cinema.auth.domain.Role;
import com.cinema.auth.domain.User;
import com.cinema.auth.dto.AuthResponse;
import com.cinema.auth.dto.LoginRequest;
import com.cinema.auth.dto.RefreshRequest;
import com.cinema.auth.dto.RegisterRequest;
import com.cinema.auth.dto.UserResponse;
import com.cinema.auth.repository.RefreshTokenRepository;
import com.cinema.auth.repository.UserRepository;
import com.cinema.auth.security.UserPrincipal;
import com.cinema.common.exception.ConflictException;
import com.cinema.common.exception.NotFoundException;
import com.cinema.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalise(request.email());

        // Racing registrations can both pass this check; uq_users_email is the real guarantee and
        // GlobalExceptionHandler already turns that violation into the same 409.
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_TAKEN", "An account with that email already exists");
        }

        User user = users.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                // Never taken from the request. Admins exist only via migration.
                .role(Role.CUSTOMER)
                .build());

        log.debug("Registered user {}", user.getId());
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(normalise(request.email()))
                .orElseThrow(AuthService::invalidCredentials);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return issueTokens(user);
    }

    /**
     * Rotates on every use: the presented token is revoked and a new one issued, so replaying an
     * already-exchanged refresh token fails.
     */
    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken stored = refreshTokenService.verify(request.refreshToken());
        refreshTokenService.revoke(stored);
        // Lazy association, but this method is transactional so the proxy initialises here.
        return issueTokens(stored.getUser());
    }

    /**
     * Revokes the one session the token belongs to. Idempotent — logging out twice, or with a
     * token that was already rotated away, is a no-op rather than an error.
     * <p>
     * The access token issued alongside it stays valid until its TTL expires. That is inherent to
     * stateless JWT; the 15 minute lifetime is what bounds it.
     */
    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokens.findByTokenHashAndRevokedFalse(RefreshTokenService.hash(request.refreshToken()))
                .ifPresent(refreshTokenService::revoke);
    }

    /** "Log out everywhere" — drops every refresh token the user holds. */
    @Transactional
    public void logoutEverywhere(Long userId) {
        refreshTokens.deleteAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UserPrincipal principal) {
        return users.findById(principal.userId())
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User", principal.userId()));
    }

    private AuthResponse issueTokens(User user) {
        return AuthResponse.of(
                jwtService.generateAccessToken(user),
                refreshTokenService.issue(user),
                jwtService.accessTokenTtl().toSeconds(),
                UserResponse.from(user));
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Identical for an unknown email and a wrong password — never leak which one was wrong. */
    private static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("INVALID_CREDENTIALS", "Email or password is incorrect");
    }
}
