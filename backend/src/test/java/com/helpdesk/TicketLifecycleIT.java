package com.helpdesk;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.helpdesk.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** create → assign → in progress → internal note → public reply → resolve → customer reopens → close. */
class TicketLifecycleIT extends AbstractIntegrationTest {

    @Test
    void fullLifecycle_recordsHistoryInOrder() {
        Tenant t = registerTenant("life");
        Member agent = createMember(t, "AGENT", "agent@life.test");
        Member customer = createMember(t, "CUSTOMER", "customer@life.test");

        Response created = createTicket(customer.token(), "Cannot reset password");
        assertThat(created.status()).isEqualTo(201);
        UUID id = created.id();
        String base = "/tickets/" + id;
        assertThat(created.text("status")).isEqualTo("OPEN");
        assertThat(created.text("priority")).isEqualTo("MEDIUM");
        assertThat(created.body().path("number").asLong()).isEqualTo(1);

        assertThat(put(base + "/assignee", agent.token(), Map.of("assigneeId", agent.id())).status()).isEqualTo(200);
        assertThat(put(base + "/status", agent.token(), Map.of("status", "IN_PROGRESS")).status()).isEqualTo(200);
        assertThat(post(base + "/comments", agent.token(), Map.of("body", "Check SMTP relay", "visibility", "INTERNAL")).status()).isEqualTo(201);
        assertThat(post(base + "/comments", agent.token(), Map.of("body", "We are on it")).status()).isEqualTo(201);

        Response resolved = put(base + "/status", agent.token(), Map.of("status", "RESOLVED", "comment", "Fixed SMTP configuration."));
        assertThat(resolved.status()).isEqualTo(200);
        assertThat(resolved.body().path("resolvedAt").isNull()).isFalse();

        // Customer: may reopen a RESOLVED ticket, and close their own ticket.
        Response customerView = get(base, customer.token());
        assertThat(customerView.body().path("allowedTransitions").findValuesAsText("")).isEmpty();
        assertThat(textList(customerView.body().path("allowedTransitions"))).containsExactly("OPEN", "CLOSED");
        Response reopened = put(base + "/status", customer.token(), Map.of("status", "OPEN"));
        assertThat(reopened.status()).isEqualTo(200);
        assertThat(reopened.body().path("resolvedAt").isNull()).isTrue();

        Response forbidden = put(base + "/status", customer.token(), Map.of("status", "IN_PROGRESS"));
        assertThat(forbidden.status()).isEqualTo(403);

        Response priority = patch(base, agent.token(), Map.of("priority", "URGENT", "version", reopened.body().path("version").asLong()));
        assertThat(priority.status()).isEqualTo(200);

        Response closed = put(base + "/status", customer.token(), Map.of("status", "CLOSED"));
        assertThat(closed.status()).isEqualTo(200);
        assertThat(closed.body().path("closedAt").isNull()).isFalse();
        assertThat(closed.body().path("permissions").path("canComment").asBoolean()).isFalse();

        // BR-6: a CLOSED ticket is read-only.
        assertThat(post(base + "/comments", agent.token(), Map.of("body", "late")).text("code")).isEqualTo("TICKET_CLOSED");
        assertThat(put(base + "/assignee", agent.token(), Map.of("assigneeId", agent.id())).text("code")).isEqualTo("TICKET_CLOSED");
        assertThat(patch(base, agent.token(), Map.of("subject", "Edited", "version", closed.body().path("version").asLong())).text("code"))
                .isEqualTo("TICKET_CLOSED");
        // Customer cannot reopen a CLOSED ticket; staff can.
        assertThat(put(base + "/status", customer.token(), Map.of("status", "OPEN")).status()).isEqualTo(403);
        assertThat(put(base + "/status", agent.token(), Map.of("status", "OPEN")).status()).isEqualTo(200);
        // Same status is not a transition.
        assertThat(put(base + "/status", agent.token(), Map.of("status", "OPEN")).text("code")).isEqualTo("INVALID_STATUS_TRANSITION");

        // History: every change, in order, with actor.
        Response history = get(base + "/history", customer.token());
        List<String> events = new ArrayList<>();
        for (JsonNode e : history.body()) {
            events.add(e.path("eventType").asText() + ":" + valueText(e.path("oldValue")) + "->" + valueText(e.path("newValue")));
        }
        assertThat(events).containsExactly(
                "CREATED:null->OPEN",
                "ASSIGNEE_CHANGED:null->AGENT agent@life.test",
                "STATUS_CHANGED:OPEN->IN_PROGRESS",
                "STATUS_CHANGED:IN_PROGRESS->RESOLVED",
                "STATUS_CHANGED:RESOLVED->OPEN",
                "PRIORITY_CHANGED:MEDIUM->URGENT",
                "STATUS_CHANGED:OPEN->CLOSED",
                "STATUS_CHANGED:CLOSED->OPEN");
        assertThat(history.body().get(4).path("actor").path("id").asText()).isEqualTo(customer.id().toString());

        // The status comment was added as a PUBLIC reply in the same transaction; internal note hidden from customer.
        Response customerComments = get(base + "/comments", customer.token());
        assertThat(customerComments.body().path("content").findValuesAsText("body"))
                .containsExactly("We are on it", "Fixed SMTP configuration.");
    }

    @Test
    void agentCreatesOnBehalfOfCustomer_andUnassign() {
        Tenant t = registerTenant("behalf");
        Member agent = createMember(t, "AGENT", "agent@behalf.test");
        Member customer = createMember(t, "CUSTOMER", "customer@behalf.test");

        Response created = post("/tickets", agent.token(), Map.of("subject", "Phoned in", "description", "Customer called", "priority", "HIGH",
                "requesterId", customer.id()));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().path("requester").path("id").asText()).isEqualTo(customer.id().toString());
        assertThat(get("/tickets/" + created.id(), customer.token()).status()).isEqualTo(200);

        Response notCustomer = post("/tickets", agent.token(), Map.of("subject", "Bad", "description", "x", "requesterId", agent.id()));
        assertThat(notCustomer.status()).isEqualTo(201);   // requesterId == self is simply "me"
        Response invalid = post("/tickets", agent.token(), Map.of("subject", "Bad", "description", "x", "requesterId", t.adminId()));
        assertThat(invalid.text("code")).isEqualTo("INVALID_REQUESTER");
        Response byCustomer = post("/tickets", customer.token(), Map.of("subject", "Bad", "description", "x", "requesterId", agent.id()));
        assertThat(byCustomer.status()).isEqualTo(403);

        put("/tickets/" + created.id() + "/assignee", agent.token(), Map.of("assigneeId", agent.id()));
        Response unassigned = delete("/tickets/" + created.id() + "/assignee", agent.token());
        assertThat(unassigned.status()).isEqualTo(200);
        assertThat(unassigned.body().path("assignee").isNull()).isTrue();
    }

    private static List<String> textList(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    private static String valueText(JsonNode v) {
        if (v.isNull() || v.isMissingNode()) {
            return "null";
        }
        return v.isObject() ? v.path("fullName").asText() : v.asText();
    }
}
