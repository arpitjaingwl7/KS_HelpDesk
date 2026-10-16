package com.helpdesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.helpdesk.support.AbstractIntegrationTest;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * The tenant isolation suite (docs/07 §4). Two organizations — Acme and Globex — each with an admin,
 * an agent and two customers. Every request below runs over HTTP with a real JWT against PostgreSQL.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)   // case01 … case16, so case16 adds its users last
class TenantIsolationIT extends AbstractIntegrationTest {

    Tenant acme;
    Tenant globex;
    Member acmeAgent;
    Member acmeCustomerA;
    Member acmeCustomerB;
    Member globexAgent;
    Member globexCustomer;
    UUID acmeTicket;          // requested by acmeCustomerA
    UUID acmeCustomerBTicket;
    int globexTicketCount;

    @BeforeAll
    void setUpTenants() {
        acme = registerTenant("acme");
        globex = registerTenant("globex");
        acmeAgent = createMember(acme, "AGENT", "agent@acme.test");
        acmeCustomerA = createMember(acme, "CUSTOMER", "customer-a@acme.test");
        acmeCustomerB = createMember(acme, "CUSTOMER", "customer-b@acme.test");
        globexAgent = createMember(globex, "AGENT", "agent@globex.test");
        globexCustomer = createMember(globex, "CUSTOMER", "customer@globex.test");

        acmeTicket = createTicket(acmeCustomerA.token(), "Acme printer jam").id();
        acmeCustomerBTicket = createTicket(acmeCustomerB.token(), "Customer B secret issue").id();
        post("/tickets/" + acmeTicket + "/comments", acmeAgent.token(), Map.of("body", "Public reply"));
        post("/tickets/" + acmeTicket + "/comments", acmeAgent.token(), Map.of("body", "Internal note", "visibility", "INTERNAL"));
        post("/tickets/" + acmeTicket + "/comments", acmeAgent.token(), Map.of("body", "Second internal note", "visibility", "INTERNAL"));

        createTicket(globexCustomer.token(), "Globex ticket 1");
        createTicket(globexCustomer.token(), "Globex ticket 2");
        globexTicketCount = 2;
    }

    @Test
    @DisplayName("#1 Globex agent GET Acme ticket → 404")
    void case01_getForeignTicket() {
        Response r = get("/tickets/" + acmeTicket, globexAgent.token());
        assertThat(r.status()).isEqualTo(404);
        assertThat(r.text("code")).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    @DisplayName("#2 Globex agent PATCH Acme ticket → 404, ticket unchanged in DB")
    void case02_patchForeignTicket() {
        Response r = patch("/tickets/" + acmeTicket, globexAgent.token(), Map.of("subject", "Hacked by Globex", "version", 0));
        assertThat(r.status()).isEqualTo(404);
        String subject = jdbc.queryForObject("select subject from tickets where id = ?", String.class, acmeTicket);
        assertThat(subject).isEqualTo("Acme printer jam");
    }

    @Test
    @DisplayName("#3 Globex agent PUT Acme ticket status → 404")
    void case03_statusForeignTicket() {
        assertThat(put("/tickets/" + acmeTicket + "/status", globexAgent.token(), Map.of("status", "CLOSED")).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("#4 Globex agent PUT/DELETE Acme ticket assignee → 404")
    void case04_assignForeignTicket() {
        assertThat(put("/tickets/" + acmeTicket + "/assignee", globexAgent.token(), Map.of("assigneeId", globexAgent.id())).status())
                .isEqualTo(404);
        assertThat(delete("/tickets/" + acmeTicket + "/assignee", globexAgent.token()).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("#5 Globex agent GET/POST Acme ticket comments → 404")
    void case05_commentsForeignTicket() {
        assertThat(get("/tickets/" + acmeTicket + "/comments", globexAgent.token()).status()).isEqualTo(404);
        assertThat(post("/tickets/" + acmeTicket + "/comments", globexAgent.token(), Map.of("body", "hi")).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("#6 Globex agent GET Acme ticket history → 404")
    void case06_historyForeignTicket() {
        assertThat(get("/tickets/" + acmeTicket + "/history", globexAgent.token()).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("#7 Globex agent GET /tickets → only Globex tickets")
    void case07_listOnlyOwnOrg() {
        Response r = get("/tickets?size=100", globexAgent.token());
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().path("page").path("totalElements").asInt()).isEqualTo(globexTicketCount);
        assertThat(subjects(r.body())).allMatch(s -> s.startsWith("Globex"));
    }

    @Test
    @DisplayName("#8 Globex agent GET /tickets?requesterId={acmeCustomer} → empty page")
    void case08_filterByForeignRequester() {
        Response r = get("/tickets?requesterId=" + acmeCustomerA.id(), globexAgent.token());
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().path("page").path("totalElements").asInt()).isZero();
    }

    @Test
    @DisplayName("#9 Acme agent assigns Acme ticket to Globex agent → 400 INVALID_ASSIGNEE")
    void case09_assignToForeignAgent() {
        Response r = put("/tickets/" + acmeTicket + "/assignee", acmeAgent.token(), Map.of("assigneeId", globexAgent.id()));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.text("code")).isEqualTo("INVALID_ASSIGNEE");
    }

    @Test
    @DisplayName("#10 Globex admin GET/PATCH Acme user → 404")
    void case10_foreignUser() {
        assertThat(get("/users/" + acmeAgent.id(), globex.adminToken()).status()).isEqualTo(404);
        assertThat(patch("/users/" + acmeAgent.id(), globex.adminToken(), Map.of("active", false, "version", 0)).status()).isEqualTo(404);
        Boolean active = jdbc.queryForObject("select active from users where id = ?", Boolean.class, acmeAgent.id());
        assertThat(active).isTrue();
    }

    @Test
    @DisplayName("#11 Globex admin GET /agents → only Globex agents")
    void case11_agentsOnlyOwnOrg() {
        Response r = get("/agents", globex.adminToken());
        Set<String> emails = StreamSupport.stream(r.body().spliterator(), false)
                .map(n -> n.path("email").asText()).collect(Collectors.toSet());
        assertThat(emails).containsExactlyInAnyOrder("agent@globex.test", "admin@" + globex.slug() + ".test");
        Response users = get("/users?size=100", globex.adminToken());
        assertThat(users.body().path("page").path("totalElements").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("#12 Acme customer A GET customer B's ticket (same org) → 404")
    void case12_otherCustomersTicket() {
        assertThat(get("/tickets/" + acmeCustomerBTicket, acmeCustomerA.token()).status()).isEqualTo(404);
        assertThat(get("/tickets/" + acmeCustomerBTicket + "/comments", acmeCustomerA.token()).status()).isEqualTo(404);
        Response list = get("/tickets?requesterId=" + acmeCustomerB.id(), acmeCustomerA.token());
        assertThat(subjects(list.body())).containsExactly("Acme printer jam");   // requesterId forced to self
    }

    @Test
    @DisplayName("#13 Acme customer GET comments → no INTERNAL, totalElements counts PUBLIC only")
    void case13_internalNotesHidden() {
        Response asCustomer = get("/tickets/" + acmeTicket + "/comments", acmeCustomerA.token());
        assertThat(asCustomer.status()).isEqualTo(200);
        assertThat(asCustomer.body().path("page").path("totalElements").asInt()).isEqualTo(1);
        assertThat(asCustomer.body().path("content").findValuesAsText("visibility")).containsOnly("PUBLIC");
        assertThat(asCustomer.toString()).doesNotContain("Internal note");

        Response asAgent = get("/tickets/" + acmeTicket + "/comments", acmeAgent.token());
        assertThat(asAgent.body().path("page").path("totalElements").asInt()).isEqualTo(3);

        Response customerPostsInternal = post("/tickets/" + acmeTicket + "/comments", acmeCustomerA.token(),
                Map.of("body", "sneaky", "visibility", "INTERNAL"));
        assertThat(customerPostsInternal.status()).isEqualTo(403);
    }

    @Test
    @DisplayName("#14 organizationId in the request body is ignored")
    void case14_bodyOrganizationIdIgnored() {
        Map<String, Object> body = new HashMap<>();
        body.put("subject", "Spoof attempt");
        body.put("description", "Trying to write into Globex");
        body.put("organizationId", globex.orgId().toString());
        Response r = post("/tickets", acmeAgent.token(), body);
        assertThat(r.status()).isEqualTo(201);
        UUID org = jdbc.queryForObject("select organization_id from tickets where id = ?", UUID.class, r.id());
        assertThat(org).isEqualTo(acme.orgId());
    }

    @Test
    @DisplayName("#15 DB-level: ticket with Acme org + Globex assignee → FK violation")
    void case15_databaseRejectsCrossTenantReference() {
        Timestamp now = Timestamp.from(Instant.now());
        assertThatThrownBy(() -> jdbc.update("""
                        insert into tickets (id, organization_id, number, subject, description, priority, status,
                                             requester_id, assignee_id, created_at, updated_at)
                        values (?, ?, 999999, 'x', 'x', 'LOW', 'OPEN', ?, ?, ?, ?)""",
                UUID.randomUUID(), acme.orgId(), acmeCustomerA.id(), globexAgent.id(), now, now))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_tickets_assignee");

        assertThatThrownBy(() -> jdbc.update("""
                        insert into ticket_comments (id, organization_id, ticket_id, author_id, body, visibility, created_at)
                        values (?, ?, ?, ?, 'x', 'PUBLIC', ?)""",
                UUID.randomUUID(), globex.orgId(), acmeTicket, globexAgent.id(), now))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_comments_ticket");
    }

    @Test
    @DisplayName("#16 Same email in two orgs → each login returns its own user and org")
    void case16_sameEmailInTwoOrgs() {
        createMember(acme, "CUSTOMER", "shared@example.test");
        createMember(globex, "AGENT", "shared@example.test");

        Response acmeLogin = post("/auth/login", null, Map.of("organizationSlug", acme.slug(), "email", "shared@example.test", "password", PASSWORD));
        Response globexLogin = post("/auth/login", null, Map.of("organizationSlug", globex.slug(), "email", "SHARED@example.test", "password", PASSWORD));

        assertThat(acmeLogin.body().path("user").path("organization").path("id").asText()).isEqualTo(acme.orgId().toString());
        assertThat(acmeLogin.body().path("user").path("role").asText()).isEqualTo("CUSTOMER");
        assertThat(globexLogin.body().path("user").path("organization").path("id").asText()).isEqualTo(globex.orgId().toString());
        assertThat(globexLogin.body().path("user").path("role").asText()).isEqualTo("AGENT");

        // A token from one org can never be used to read the other org's data.
        Response me = get("/auth/me", acmeLogin.text("accessToken"));
        assertThat(me.body().path("organization").path("slug").asText()).isEqualTo(acme.slug());
    }

    private static java.util.List<String> subjects(JsonNode page) {
        return page.path("content").findValuesAsText("subject");
    }
}
