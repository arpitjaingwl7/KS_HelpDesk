package com.helpdesk;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.helpdesk.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpMethod;

/** Every filter, combinations, free text, business-order priority sort and page metadata (FR-16 – FR-19). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TicketSearchIT extends AbstractIntegrationTest {

    static final String[] PRIORITIES = {"LOW", "MEDIUM", "HIGH", "URGENT"};

    Tenant tenant;
    Member agent;
    Member customerA;
    Member customerB;

    @BeforeAll
    void seed() {
        tenant = registerTenant("search");
        agent = createMember(tenant, "AGENT", "agent@search.test");
        customerA = createMember(tenant, "CUSTOMER", "a@search.test");
        customerB = createMember(tenant, "CUSTOMER", "b@search.test");
        // 60 tickets: 40 by customer A, 20 by customer B; priority cycles; every 3rd assigned; every 5th IN_PROGRESS.
        for (int i = 1; i <= 60; i++) {
            Member requester = i <= 40 ? customerA : customerB;
            String subject = (i % 10 == 0 ? "Password reset broken " : "Billing question ") + i;
            Response r = post("/tickets", agent.token(), Map.of("subject", subject, "description", "Body 100% unique_" + i,
                    "priority", PRIORITIES[i % 4], "requesterId", requester.id()));
            UUID id = r.id();
            if (i % 3 == 0) {
                put("/tickets/" + id + "/assignee", agent.token(), Map.of("assigneeId", agent.id()));
            }
            if (i % 5 == 0) {
                put("/tickets/" + id + "/status", agent.token(), Map.of("status", "IN_PROGRESS"));
            }
        }
    }

    private int total(String query, String token) {
        Response r = get("/tickets?" + query, token);
        assertThat(r.status()).as(query).isEqualTo(200);
        return r.body().path("page").path("totalElements").asInt();
    }

    @Test
    void filters_combineWithAnd_valuesWithOr() {
        assertThat(total("", agent.token())).isEqualTo(60);
        assertThat(total("status=IN_PROGRESS", agent.token())).isEqualTo(12);
        assertThat(total("status=OPEN&status=IN_PROGRESS", agent.token())).isEqualTo(60);
        assertThat(total("status=OPEN,IN_PROGRESS", agent.token())).isEqualTo(60);
        assertThat(total("priority=URGENT", agent.token())).isEqualTo(15);
        assertThat(total("priority=HIGH,URGENT", agent.token())).isEqualTo(30);
        assertThat(total("assigneeId=" + agent.id(), agent.token())).isEqualTo(20);
        assertThat(total("unassigned=true", agent.token())).isEqualTo(40);
        assertThat(total("requesterId=" + customerB.id(), agent.token())).isEqualTo(20);
        // i%15==0 → assigned AND in progress: 15, 30, 45, 60
        assertThat(total("status=IN_PROGRESS&assigneeId=" + agent.id(), agent.token())).isEqualTo(4);
    }

    @Test
    void assigneeAndUnassigned_together_is400() {
        Response r = get("/tickets?unassigned=true&assigneeId=" + agent.id(), agent.token());
        assertThat(r.status()).isEqualTo(400);
    }

    @Test
    void freeText_matchesSubjectDescriptionAndNumber_caseInsensitive() {
        assertThat(total("q=PASSWORD", agent.token())).isEqualTo(6);
        assertThat(total("q=unique_42", agent.token())).isEqualTo(1);
        assertThat(search("100%")).isEqualTo(60);       // literal % is escaped, not a wildcard
        assertThat(search("1%")).isZero();              // … so "1%" does not match "100%"
        assertThat(search("_")).isEqualTo(60);          // literal _ (every description has one)
        Response byNumber = call(HttpMethod.GET, "/tickets?q={q}", agent.token(), null, "#17");
        assertThat(byNumber.body().path("content").findValuesAsText("number")).contains("17");
    }

    private int search(String q) {
        return call(HttpMethod.GET, "/tickets?q={q}", agent.token(), null, q).body().path("page").path("totalElements").asInt();
    }

    @Test
    void customer_isAlwaysLimitedToOwnTickets() {
        assertThat(total("", customerA.token())).isEqualTo(40);
        assertThat(total("requesterId=" + customerB.id(), customerA.token())).isEqualTo(40);
        assertThat(total("unassigned=true", customerA.token())).isEqualTo(40);   // assignee filters ignored for customers
        assertThat(total("", customerB.token())).isEqualTo(20);
    }

    @Test
    void sortByPriority_usesBusinessOrder() {
        Response r = get("/tickets?sort=priority,desc&sort=number,asc&size=60", agent.token());
        List<String> priorities = r.body().path("content").findValuesAsText("priority");
        assertThat(priorities.subList(0, 15)).containsOnly("URGENT");
        assertThat(priorities.subList(15, 30)).containsOnly("HIGH");
        assertThat(priorities.subList(30, 45)).containsOnly("MEDIUM");
        assertThat(priorities.subList(45, 60)).containsOnly("LOW");
    }

    @Test
    void pagination_metadata_andSizeCap() {
        Response page2 = get("/tickets?page=2&size=25&sort=number,asc", agent.token());
        JsonNode meta = page2.body().path("page");
        assertThat(meta.path("number").asInt()).isEqualTo(2);
        assertThat(meta.path("size").asInt()).isEqualTo(25);
        assertThat(meta.path("totalElements").asInt()).isEqualTo(60);
        assertThat(meta.path("totalPages").asInt()).isEqualTo(3);
        List<Long> numbers = new ArrayList<>();
        page2.body().path("content").forEach(n -> numbers.add(n.path("number").asLong()));
        assertThat(numbers).containsExactly(51L, 52L, 53L, 54L, 55L, 56L, 57L, 58L, 59L, 60L);

        Response capped = get("/tickets?size=1000", agent.token());
        assertThat(capped.body().path("page").path("size").asInt()).isEqualTo(100);

        Response defaultSort = get("/tickets?size=1", agent.token());
        assertThat(defaultSort.body().path("content").get(0).path("number").asLong()).isEqualTo(60);   // createdAt,desc

        assertThat(get("/tickets?sort=description,asc", agent.token()).text("code")).isEqualTo("INVALID_SORT");
    }
}
