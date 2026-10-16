package com.helpdesk;

import static org.assertj.core.api.Assertions.assertThat;

import com.helpdesk.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

/** Per-org ticket numbers under parallel creation, and optimistic locking (BR-2, FR-13). */
class ConcurrencyIT extends AbstractIntegrationTest {

    @Test
    void ticketNumbers_areUniqueAndConsecutive_underParallelCreates() throws Exception {
        Tenant tenant = registerTenant("numbers");
        Tenant other = registerTenant("numbers-other");
        Member customer = createMember(tenant, "CUSTOMER", "c@numbers.test");

        List<Callable<Long>> creates = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            int n = i;
            creates.add(() -> createTicket(customer.token(), "Parallel ticket " + n).body().path("number").asLong());
        }
        List<Long> numbers = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            for (Future<Long> f : pool.invokeAll(creates)) {
                numbers.add(f.get());
            }
        }
        assertThat(numbers).containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(1, 20).boxed().toList());

        // Another org's sequence is independent.
        assertThat(createTicket(other.adminToken(), "First in other org").body().path("number").asLong()).isEqualTo(1);
    }

    @Test
    void twoPatchesWithSameVersion_oneWins_otherGets409() {
        Tenant tenant = registerTenant("locking");
        Member agent = createMember(tenant, "AGENT", "a@locking.test");
        Response created = createTicket(agent.token(), "Contended ticket");
        long version = created.body().path("version").asLong();
        String path = "/tickets/" + created.id();

        Response first = patch(path, agent.token(), Map.of("subject", "First edit", "version", version));
        Response second = patch(path, agent.token(), Map.of("subject", "Second edit", "version", version));

        assertThat(first.status()).isEqualTo(200);
        assertThat(first.body().path("version").asLong()).isEqualTo(version + 1);
        assertThat(second.status()).isEqualTo(409);
        assertThat(second.text("code")).isEqualTo("CONCURRENT_MODIFICATION");
        assertThat(get(path, agent.token()).text("subject")).isEqualTo("First edit");
    }
}
