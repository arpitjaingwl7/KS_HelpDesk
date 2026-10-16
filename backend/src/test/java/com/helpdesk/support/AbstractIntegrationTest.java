package com.helpdesk.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

/**
 * Full stack over real HTTP with real JWTs against PostgreSQL 16 (see {@link IntegrationDatabase}).
 * Tests never share data: every test class registers its own organizations with unique slugs,
 * so no cleanup is needed and classes can share one database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnabledIf(value = "com.helpdesk.support.IntegrationDatabase#available",
        disabledReason = "Needs Docker (Testcontainers) or IT_DB_URL pointing at PostgreSQL 16")
public abstract class AbstractIntegrationTest {

    public static final String PASSWORD = "Password123!";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", IntegrationDatabase::url);
        registry.add("spring.datasource.username", IntegrationDatabase::username);
        registry.add("spring.datasource.password", IntegrationDatabase::password);
    }

    @LocalServerPort
    int port;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected JdbcTemplate jdbc;

    private RestClient http;

    /** Built lazily so it is also available in PER_CLASS {@code @BeforeAll} seeding. */
    protected RestClient http() {
        if (http == null) {
            http = RestClient.builder()
                    .baseUrl("http://localhost:" + port + "/api/v1")
                    .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newHttpClient()))
                    .defaultStatusHandler(status -> true, (req, res) -> { })   // never throw; tests assert on status
                    .build();
        }
        return http;
    }

    // ───────────── HTTP helpers ─────────────

    public record Response(int status, JsonNode body) {
        public String text(String field) {
            return body.path(field).asText();
        }

        public UUID id() {
            return UUID.fromString(body.path("id").asText());
        }
    }

    protected Response call(HttpMethod method, String path, String token, Object body, Object... uriVariables) {
        RestClient.RequestBodySpec spec = http().method(method).uri(path, uriVariables).accept(MediaType.APPLICATION_JSON);
        if (token != null) {
            spec.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return spec.exchange((req, res) -> {
            byte[] bytes = res.getBody().readAllBytes();
            JsonNode node = bytes.length == 0 ? json.nullNode() : json.readTree(bytes);
            return new Response(res.getStatusCode().value(), node);
        });
    }

    protected Response get(String path, String token) {
        return call(HttpMethod.GET, path, token, null);
    }

    protected Response post(String path, String token, Object body) {
        return call(HttpMethod.POST, path, token, body);
    }

    protected Response put(String path, String token, Object body) {
        return call(HttpMethod.PUT, path, token, body);
    }

    protected Response patch(String path, String token, Object body) {
        return call(HttpMethod.PATCH, path, token, body);
    }

    protected Response delete(String path, String token) {
        return call(HttpMethod.DELETE, path, token, null);
    }

    // ───────────── tenant fixtures ─────────────

    /** A registered organization with its admin's token. */
    public record Tenant(String slug, UUID orgId, UUID adminId, String adminToken) {
    }

    /** A user created inside a tenant, already logged in. */
    public record Member(UUID id, String email, String token) {
    }

    protected static String uniqueSlug(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected Tenant registerTenant(String prefix) {
        String slug = uniqueSlug(prefix);
        Response r = post("/auth/register-organization", null, Map.of(
                "organizationName", prefix + " Org", "organizationSlug", slug,
                "adminFullName", prefix + " Admin", "adminEmail", "admin@" + slug + ".test", "adminPassword", PASSWORD));
        if (r.status() != 201) {
            throw new IllegalStateException("registration failed: " + r);
        }
        JsonNode user = r.body().path("user");
        return new Tenant(slug, UUID.fromString(user.path("organization").path("id").asText()),
                UUID.fromString(user.path("id").asText()), r.text("accessToken"));
    }

    protected Member createMember(Tenant tenant, String role, String email) {
        Response r = post("/users", tenant.adminToken(), Map.of(
                "fullName", role + " " + email, "email", email, "password", PASSWORD, "role", role));
        if (r.status() != 201) {
            throw new IllegalStateException("user creation failed: " + r);
        }
        return new Member(r.id(), email, login(tenant.slug(), email));
    }

    protected String login(String slug, String email) {
        Response r = post("/auth/login", null, Map.of("organizationSlug", slug, "email", email, "password", PASSWORD));
        if (r.status() != 200) {
            throw new IllegalStateException("login failed: " + r);
        }
        return r.text("accessToken");
    }

    protected Response createTicket(String token, String subject) {
        return post("/tickets", token, Map.of("subject", subject, "description", "Description of " + subject));
    }
}
