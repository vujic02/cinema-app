package com.cinema.auth;

import com.cinema.auth.domain.RefreshToken;
import com.cinema.auth.domain.User;
import com.cinema.auth.dto.LoginRequest;
import com.cinema.auth.dto.RefreshRequest;
import com.cinema.auth.dto.RegisterRequest;
import com.cinema.auth.repository.RefreshTokenRepository;
import com.cinema.auth.repository.UserRepository;
import com.cinema.auth.service.RefreshTokenService;
import com.cinema.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Part 2 acceptance: register/login happy paths, bad credentials, and the two refresh-token
 * failure modes (expired, revoked).
 * <p>
 * Tests register users under unique emails rather than rolling back in a transaction, so the
 * requests go through exactly the same commit path they would in production.
 */
class AuthIntegrationTest extends AbstractIntegrationTest {

    private static final String SEEDED_CUSTOMER = "customer@lumen.test";
    private static final String CUSTOMER_PASSWORD = "password123";
    private static final String SEEDED_ADMIN = "admin@lumen.test";
    private static final String ADMIN_PASSWORD = "admin123";

    @Autowired
    private UserRepository users;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    // ---------------------------------------------------------------- register

    @Test
    @DisplayName("register returns 201 with a token pair and a CUSTOMER account")
    void registerHappyPath() throws Exception {
        RegisterRequest request = new RegisterRequest(uniqueEmail(), "supersecret1", "Test Person");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value(request.email()))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                // The hash must never leave the server, under any property name.
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("register stores a BCrypt hash, never the plaintext")
    void registerHashesPassword() throws Exception {
        RegisterRequest request = new RegisterRequest(uniqueEmail(), "supersecret1", "Hash Check");
        register(request);

        User saved = users.findByEmailIgnoreCase(request.email()).orElseThrow();
        assertThat(saved.getPasswordHash())
                .isNotEqualTo(request.password())
                .startsWith("$2a$");
    }

    @Test
    @DisplayName("register cannot self-assign ADMIN — the role field is ignored")
    void registerCannotEscalateToAdmin() throws Exception {
        String email = uniqueEmail();
        String payload = """
                {"email":"%s","password":"supersecret1","fullName":"Sneaky","role":"ADMIN"}
                """.formatted(email);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"));
    }

    @Test
    @DisplayName("register with an email already taken returns 409 EMAIL_TAKEN")
    void registerDuplicateEmail() throws Exception {
        RegisterRequest request = new RegisterRequest(uniqueEmail(), "supersecret1", "First Person");
        register(request);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    @DisplayName("register rejects a short password and a malformed email with field errors")
    void registerValidation() throws Exception {
        RegisterRequest request = new RegisterRequest("not-an-email", "short", "");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.password").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.fullName").isNotEmpty());
    }

    // ------------------------------------------------------------------- login

    @Test
    @DisplayName("seeded customer can log in — proves the migration's BCrypt hashes still verify")
    void loginSeededCustomer() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(SEEDED_CUSTOMER, CUSTOMER_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(SEEDED_CUSTOMER))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"));
    }

    @Test
    @DisplayName("seeded admin logs in with the ADMIN role")
    void loginSeededAdmin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(SEEDED_ADMIN, ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    @DisplayName("login is case-insensitive on the email")
    void loginIgnoresEmailCase() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("  CUSTOMER@LUMEN.TEST  ", CUSTOMER_PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a wrong password and an unknown email give the identical 401, leaking neither")
    void loginBadCredentials() throws Exception {
        String wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(SEEDED_CUSTOMER, "definitely-wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("nobody@lumen.test", "definitely-wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        assertThat(messageOf(wrongPassword)).isEqualTo(messageOf(unknownEmail));
    }

    // --------------------------------------------------------------------- me

    @Test
    @DisplayName("GET /me without a token is 401 — not Spring's default 403")
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/auth/me"));
    }

    @Test
    @DisplayName("GET /me with a garbage bearer token is 401, not 500")
    void meRejectsMalformedToken() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /me with a valid token returns the caller")
    void meReturnsCurrentUser() throws Exception {
        JsonNode tokens = login(SEEDED_CUSTOMER, CUSTOMER_PASSWORD);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + tokens.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(SEEDED_CUSTOMER))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.fullName").value("Jamie Rivera"));
    }

    // ---------------------------------------------------------------- refresh

    @Nested
    @DisplayName("refresh")
    class Refresh {

        @Test
        @DisplayName("exchanges a valid refresh token for a fresh pair")
        void refreshHappyPath() throws Exception {
            JsonNode first = login(SEEDED_CUSTOMER, CUSTOMER_PASSWORD);

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new RefreshRequest(first.get("refreshToken").asText()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.user.email").value(SEEDED_CUSTOMER));
        }

        @Test
        @DisplayName("rotates: replaying an already-exchanged token is rejected")
        void refreshRotatesToken() throws Exception {
            String original = login(SEEDED_CUSTOMER, CUSTOMER_PASSWORD).get("refreshToken").asText();

            String rotated = objectMapper.readTree(mockMvc.perform(post("/api/auth/refresh")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(new RefreshRequest(original))))
                            .andExpect(status().isOk())
                            .andReturn().getResponse().getContentAsString())
                    .get("refreshToken").asText();

            assertThat(rotated).isNotEqualTo(original);

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RefreshRequest(original))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        }

        /**
         * The row is written directly with an expiry in the past. Waiting out a real TTL would
         * mean a 14 day sleep.
         */
        @Test
        @DisplayName("an expired token is rejected with REFRESH_TOKEN_EXPIRED")
        void refreshRejectsExpiredToken() throws Exception {
            String raw = persistRefreshToken(Instant.now().minus(1, ChronoUnit.DAYS), false);

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RefreshRequest(raw))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_EXPIRED"));
        }

        @Test
        @DisplayName("a revoked token is rejected even while still inside its expiry window")
        void refreshRejectsRevokedToken() throws Exception {
            String raw = persistRefreshToken(Instant.now().plus(7, ChronoUnit.DAYS), true);

            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RefreshRequest(raw))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        }

        @Test
        @DisplayName("an unknown token is rejected")
        void refreshRejectsUnknownToken() throws Exception {
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new RefreshRequest("this-token-was-never-issued"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        }
    }

    // ----------------------------------------------------------------- logout

    @Test
    @DisplayName("logout revokes the session, so the token can no longer be refreshed")
    void logoutRevokesRefreshToken() throws Exception {
        String refreshToken = login(SEEDED_CUSTOMER, CUSTOMER_PASSWORD).get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("logout is idempotent — a second call with the same token still succeeds")
    void logoutIsIdempotent() throws Exception {
        String refreshToken = login(SEEDED_CUSTOMER, CUSTOMER_PASSWORD).get("refreshToken").asText();
        String body = objectMapper.writeValueAsString(new RefreshRequest(refreshToken));

        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("logout-all drops every session the user holds")
    void logoutEverywhere() throws Exception {
        RegisterRequest request = new RegisterRequest(uniqueEmail(), "supersecret1", "Multi Device");
        register(request);

        JsonNode phone = login(request.email(), request.password());
        JsonNode laptop = login(request.email(), request.password());

        mockMvc.perform(post("/api/auth/logout-all")
                        .header("Authorization", "Bearer " + laptop.get("accessToken").asText()))
                .andExpect(status().isNoContent());

        for (JsonNode session : new JsonNode[]{phone, laptop}) {
            mockMvc.perform(post("/api/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new RefreshRequest(session.get("refreshToken").asText()))))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("logout-all without a token is 401")
    void logoutEverywhereRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/logout-all"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------- public route whitelist

    @Test
    @DisplayName("the Part 1 public routes are still reachable without a token")
    void publicRoutesStayOpen() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());

        // Health may report 503 with no Redis container running; what matters here is only that
        // security does not intercept it.
        int health = mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus();
        assertThat(health).isNotIn(401, 403);
    }

    // ----------------------------------------------------------------- helpers

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@lumen.test";
    }

    private void register(RegisterRequest request) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private JsonNode login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** Writes a refresh token row straight to the database so its state can be dictated. */
    private String persistRefreshToken(Instant expiresAt, boolean revoked) {
        User user = users.findByEmailIgnoreCase(SEEDED_CUSTOMER).orElseThrow();
        String raw = "raw-token-" + UUID.randomUUID();

        refreshTokens.save(RefreshToken.builder()
                .user(user)
                .tokenHash(RefreshTokenService.hash(raw))
                .expiresAt(expiresAt)
                .revoked(revoked)
                .build());

        return raw;
    }

    private String messageOf(String responseBody) throws Exception {
        return objectMapper.readTree(responseBody).get("message").asText();
    }
}
