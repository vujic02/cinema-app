package com.cinema.config;

import com.cinema.auth.security.JwtAuthenticationFilter;
import com.cinema.auth.security.RestAuthErrorHandler;
import com.cinema.auth.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT (TECH.md §3a). No sessions, no cookies, no form login.
 */
@Configuration
@EnableWebSecurity
// Boot 3 does not switch @PreAuthorize on by itself — without this, every method-level rule in
// Parts 3-5 would silently permit everything.
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** Kept open so the Part 1 verification (health + Swagger reachable) still holds. */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/v3/api-docs",
            "/v3/api-docs/**",
            // /swagger-ui.html only redirects; the page itself lives under /swagger-ui/.
            "/swagger-ui.html",
            "/swagger-ui/**",
            // SockJS handshake and its /info negotiation. Subscribing to
            // /topic/showings/{id} needs no account because the topic carries nothing
            // user-specific — a seat id and a status, never who holds it (see SeatStatusEvent).
            // Placing a hold still requires a token; that goes over REST.
            "/ws/**"
    };

    /** Catalogue reads (Part 3). Public per the TECH.md customer flow: browse, then log in to book. */
    private static final String[] PUBLIC_CATALOGUE = {
            "/api/movies",
            "/api/movies/**",
            "/api/venues",
            "/api/venues/**",
            "/api/showings",
            "/api/showings/**"
    };

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        RestAuthErrorHandler authErrors = new RestAuthErrorHandler(objectMapper);

        return http
                // No cookies or sessions are used — CSRF tokens protect nothing here.
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // Logout is public because it authenticates with the refresh token in the
                        // body: a client whose access token already expired must still be able to
                        // end its session.
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout").permitAll()
                        // Browsing the catalogue needs no account — GET only, so the seat-hold
                        // POSTs that Part 4 adds under /api/showings/** still require one.
                        .requestMatchers(HttpMethod.GET, PUBLIC_CATALOGUE).permitAll()
                        // Belt and braces with the class-level @PreAuthorize on the admin
                        // controllers: a new admin endpoint is locked down even if someone
                        // forgets the annotation.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authErrors)
                        .accessDeniedHandler(authErrors))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Plain BCrypt, not a DelegatingPasswordEncoder. The seeded hashes in
     * V20260802_120100__seed_reference_data.sql are raw {@code $2a$10$…} with no {@code {bcrypt}}
     * prefix, so delegating would fail every seeded login with
     * {@code There is no PasswordEncoder mapped for the id "null"}.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
