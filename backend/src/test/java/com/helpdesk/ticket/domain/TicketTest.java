package com.helpdesk.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.helpdesk.common.error.ApiException;
import com.helpdesk.common.error.ErrorCode;
import com.helpdesk.security.Role;
import com.helpdesk.support.Fixtures;
import com.helpdesk.user.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketTest {

    private final UUID orgId = UUID.randomUUID();
    private final User customer = Fixtures.user(orgId, Role.CUSTOMER);
    private final User agent = Fixtures.user(orgId, Role.AGENT);

    @Test
    void open_defaultsToMediumAndOpen() {
        Ticket t = Ticket.open(orgId, 1, "Subject", "Description", null, customer);
        assertThat(t.getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(t.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(t.getOrganizationId()).isEqualTo(orgId);
        assertThat(t.isRequestedBy(customer.getId())).isTrue();
    }

    @Test
    void open_rejectsRequesterFromOtherOrg() {
        User foreign = Fixtures.user(UUID.randomUUID(), Role.CUSTOMER);
        assertThatThrownBy(() -> Ticket.open(orgId, 1, "Subject", "Description", null, foreign))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assignTo_rejectsUserFromOtherOrg() {
        Ticket t = Fixtures.ticket(customer);
        User foreignAgent = Fixtures.user(UUID.randomUUID(), Role.AGENT);
        assertThatThrownBy(() -> t.assignTo(foreignAgent)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assignTo_rejectsCustomerOrInactiveAgent() {
        Ticket t = Fixtures.ticket(customer);
        assertThatThrownBy(() -> t.assignTo(customer)).extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.INVALID_ASSIGNEE);
        assertThatThrownBy(() -> t.assignTo(Fixtures.inactive(Fixtures.user(orgId, Role.AGENT))))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.INVALID_ASSIGNEE);
    }

    @Test
    void assignAndUnassign() {
        Ticket t = Fixtures.ticket(customer);
        t.assignTo(agent);
        assertThat(t.assigneeId()).isEqualTo(agent.getId());
        t.unassign();
        assertThat(t.assigneeId()).isNull();
    }

    @Test
    void closedTicket_isReadOnly() {
        Ticket t = Fixtures.ticket(customer, TicketStatus.CLOSED);
        assertThatThrownBy(() -> t.updateDetails("New subject", null, null))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.TICKET_CLOSED);
        assertThatThrownBy(() -> t.assignTo(agent))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.TICKET_CLOSED);
        assertThatThrownBy(t::unassign)
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.TICKET_CLOSED);
    }

    @Test
    void updateDetails_appliesOnlyNonNullFields() {
        Ticket t = Fixtures.ticket(customer);
        t.updateDetails(null, "New description", TicketPriority.URGENT);
        assertThat(t.getSubject()).isEqualTo("Cannot reset password");
        assertThat(t.getDescription()).isEqualTo("New description");
        assertThat(t.getPriority()).isEqualTo(TicketPriority.URGENT);
    }
}
