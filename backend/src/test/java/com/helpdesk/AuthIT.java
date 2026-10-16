package com.helpdesk;

import static org.assertj.core.api.Assertions.assertThat;

import com.helpdesk.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthIT extends AbstractIntegrationTest {

    @Test
    void register_login_me() {
        Tenant t = registerTenant("auth");
        Response me = get("/auth/me", t.adminToken());
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.text("role")).isEqualTo("ORG_ADMIN");
        assertThat(me.body().path("organization").path("slug").asText()).isEqualTo(t.slug());

        String token = login(t.slug(), "ADMIN@" + t.slug().toUpperCase() + ".TEST");   // email and slug are case-insensitive
        assertThat(get("/auth/me", token).status()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select last_login_at is not null from users where id = ?", Boolean.class, t.adminId())).isTrue();
    }

    @Test
    void register_duplicateSlug_returns409() {
        Tenant t = registerTenant("dup");
        Response r = post("/auth/register-organization", null, Map.of("organizationName", "Again", "organizationSlug", t.slug(),
                "adminFullName", "X", "adminEmail", "x@x.test", "adminPassword", PASSWORD));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.text("code")).isEqualTo("SLUG_ALREADY_EXISTS");
    }

    @Test
    void login_failures_areIndistinguishable() {
        Tenant t = registerTenant("creds");
        Response wrongPassword = post("/auth/login", null, Map.of("organizationSlug", t.slug(), "email", "admin@" + t.slug() + ".test", "password", "WrongPass1!"));
        Response unknownOrg = post("/auth/login", null, Map.of("organizationSlug", "no-such-org-xyz", "email", "admin@x.test", "password", PASSWORD));
        Response unknownEmail = post("/auth/login", null, Map.of("organizationSlug", t.slug(), "email", "ghost@x.test", "password", PASSWORD));
        for (Response r : new Response[]{wrongPassword, unknownOrg, unknownEmail}) {
            assertThat(r.status()).isEqualTo(401);
            assertThat(r.text("code")).isEqualTo("INVALID_CREDENTIALS");
            assertThat(r.text("detail")).isEqualTo(wrongPassword.text("detail"));
        }
    }

    @Test
    void deactivatedUser_tokenRejectedOnNextRequest_andCannotLogIn() {
        Tenant t = registerTenant("deact");
        Member agent = createMember(t, "AGENT", "agent@deact.test");
        assertThat(get("/tickets", agent.token()).status()).isEqualTo(200);

        Response user = get("/users/" + agent.id(), t.adminToken());
        Response patched = patch("/users/" + agent.id(), t.adminToken(), Map.of("active", false, "version", user.body().path("version").asLong()));
        assertThat(patched.status()).isEqualTo(200);

        Response afterDeactivation = get("/tickets", agent.token());
        assertThat(afterDeactivation.status()).isEqualTo(401);
        assertThat(afterDeactivation.text("code")).isEqualTo("UNAUTHENTICATED");
        Response relogin = post("/auth/login", null, Map.of("organizationSlug", t.slug(), "email", agent.email(), "password", PASSWORD));
        assertThat(relogin.status()).isEqualTo(401);
    }

    @Test
    void roleChange_appliesImmediately_toExistingToken() {
        Tenant t = registerTenant("role");
        Member agent = createMember(t, "AGENT", "agent@role.test");
        assertThat(get("/agents", agent.token()).status()).isEqualTo(200);

        Response user = get("/users/" + agent.id(), t.adminToken());
        patch("/users/" + agent.id(), t.adminToken(), Map.of("role", "CUSTOMER", "version", user.body().path("version").asLong()));

        assertThat(get("/agents", agent.token()).status()).isEqualTo(403);
    }

    @Test
    void lastAdmin_andSelfDeactivation_areProtected() {
        Tenant t = registerTenant("admins");
        Response self = get("/users/" + t.adminId(), t.adminToken());
        long version = self.body().path("version").asLong();
        assertThat(patch("/users/" + t.adminId(), t.adminToken(), Map.of("active", false, "version", version)).text("code"))
                .isEqualTo("SELF_DEACTIVATION");
        assertThat(patch("/users/" + t.adminId(), t.adminToken(), Map.of("role", "AGENT", "version", version)).text("code"))
                .isEqualTo("LAST_ADMIN");
    }

    @Test
    void duplicateEmail_inSameOrg_returns409() {
        Tenant t = registerTenant("email");
        createMember(t, "AGENT", "bob@email.test");
        Response r = post("/users", t.adminToken(), Map.of("fullName", "Bob 2", "email", "BOB@email.test", "password", PASSWORD, "role", "AGENT"));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.text("code")).isEqualTo("EMAIL_ALREADY_EXISTS");
    }
}
