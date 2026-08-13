package com.cinema.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for every integration test: real MySQL in a container, never H2 (TECH.md §3).
 * <p>
 * H2 is not an option here even in compatibility mode — the schema leans on MySQL CHECK
 * constraints, {@code DATETIME(6)} precision, and the reserved-word workarounds
 * ({@code venue_rows}, {@code row_index}) that only mean anything on MySQL 8.
 * <p>
 * The container is started once for the whole JVM and deliberately never stopped by JUnit — the
 * "singleton container" pattern. It is <b>not</b> annotated with {@code @Container}/
 * {@code @Testcontainers}: that extension stops a static container in the {@code afterAll} of
 * every class that declares it, so with a base class shared across test classes the first class
 * to finish would tear the database out from under all the others. Ryuk reaps it at JVM exit.
 * <p>
 * Flyway runs both migrations against it, so the seeded users and venues are available to tests.
 * <p>
 * <b>Requires a running Docker daemon.</b> Without one the suite errors out at startup rather
 * than failing an assertion.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"));

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    // Seeded by V20260802_120100__seed_reference_data.sql.
    protected static final String ADMIN_EMAIL = "admin@lumen.test";
    protected static final String ADMIN_PASSWORD = "admin123";
    protected static final String CUSTOMER_EMAIL = "customer@lumen.test";
    protected static final String CUSTOMER_PASSWORD = "password123";

    /** Ready-to-use {@code Authorization} header value for the seeded admin. */
    protected String adminBearer() throws Exception {
        return bearerFor(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    /** Ready-to-use {@code Authorization} header value for a seeded customer. */
    protected String customerBearer() throws Exception {
        return bearerFor(CUSTOMER_EMAIL, CUSTOMER_PASSWORD);
    }

    protected String bearerFor(String email, String password) throws Exception {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return "Bearer " + objectMapper.readTree(response).get("accessToken").asText();
    }

    protected JsonNode asJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
