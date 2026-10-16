package com.helpdesk;

import static org.assertj.core.api.Assertions.assertThat;

import com.helpdesk.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Migrations apply cleanly and Hibernate {@code ddl-auto=validate} accepts the schema
 * (the context would not start otherwise).
 */
class FlywayMigrationIT extends AbstractIntegrationTest {

    @Test
    void migrationsApplied_andSchemaValid() {
        List<String> versions = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);
        assertThat(versions).contains("1");

        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);
        assertThat(tables).contains("organizations", "users", "tickets", "ticket_comments", "ticket_history");

        assertThat(jdbc.queryForObject("select count(*) from pg_extension where extname = 'pg_trgm'", Integer.class)).isEqualTo(1);
    }

    @Test
    void indexesUsedForTenantScopedList() {
        // Every hot query starts with organization_id; the composite indexes must exist.
        List<String> indexes = jdbc.queryForList("select indexname from pg_indexes where tablename = 'tickets'", String.class);
        assertThat(indexes).contains("ix_tickets_org_status_created", "ix_tickets_org_assignee_status",
                "ix_tickets_org_requester_created", "ix_tickets_org_priority", "ix_tickets_subject_trgm");
    }
}
