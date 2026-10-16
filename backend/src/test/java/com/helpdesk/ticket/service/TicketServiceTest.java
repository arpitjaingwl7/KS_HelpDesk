package com.helpdesk.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.helpdesk.common.error.ApiException;
import com.helpdesk.common.error.ErrorCode;
import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.security.Role;
import com.helpdesk.support.Fixtures;
import com.helpdesk.ticket.domain.Ticket;
import com.helpdesk.ticket.domain.TicketAccessPolicy;
import com.helpdesk.ticket.domain.TicketPriority;
import com.helpdesk.ticket.domain.TicketStatus;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.ticket.web.TicketMapperImpl;
import com.helpdesk.ticket.web.dto.CreateTicketRequest;
import com.helpdesk.ticket.web.dto.TicketDetail;
import com.helpdesk.ticket.web.dto.UpdateTicketRequest;
import com.helpdesk.user.User;
import com.helpdesk.user.UserService;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    TicketRepository tickets;
    @Mock
    UserService userService;
    @Mock
    TicketAuditTrail historyRecorder;
    @Mock
    StatusChangeCommentWriter commentWriter;

    TicketService service;

    final UUID orgId = UUID.randomUUID();
    final User customer = Fixtures.user(orgId, Role.CUSTOMER);
    final User otherCustomer = Fixtures.user(orgId, Role.CUSTOMER);
    final User agent = Fixtures.user(orgId, Role.AGENT);
    final User agent2 = Fixtures.user(orgId, Role.AGENT);
    final AuthenticatedUser customerActor = Fixtures.actor(customer);
    final AuthenticatedUser agentActor = Fixtures.actor(agent);

    @BeforeEach
    void setUp() {
        TicketAccessPolicy policy = new TicketAccessPolicy(tickets);
        TicketQueryService queries = new TicketQueryService(tickets, policy, new TicketMapperImpl());
        Clock clock = Clock.fixed(Fixtures.NOW, ZoneOffset.UTC);
        service = new TicketService(tickets, policy, userService, historyRecorder, commentWriter, queries, clock);
    }

    private Ticket stored(Ticket t) {
        when(tickets.findByIdAndOrganizationId(t.getId(), orgId)).thenReturn(Optional.of(t));
        return t;
    }

    // ───────────── create ─────────────

    @Test
    void create_setsOrgAndRequesterFromActor() {
        when(userService.requireActor(customerActor)).thenReturn(customer);
        when(tickets.nextTicketNumber(orgId)).thenReturn(7L);

        TicketDetail detail = service.create(customerActor, new CreateTicketRequest("Printer on fire", "Help", null, null));

        ArgumentCaptor<Ticket> saved = ArgumentCaptor.forClass(Ticket.class);
        verify(tickets).saveAndFlush(saved.capture());
        Ticket t = saved.getValue();
        assertThat(t.getOrganizationId()).isEqualTo(orgId);
        assertThat(t.getRequester()).isSameAs(customer);
        assertThat(t.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(t.getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(t.getNumber()).isEqualTo(7L);
        assertThat(detail.number()).isEqualTo(7L);
        assertThat(detail.allowedTransitions()).containsExactly(TicketStatus.CLOSED);
    }

    @Test
    void create_byAgentOnBehalfOfCustomer_setsRequester() {
        when(userService.findActiveCustomer(orgId, customer.getId())).thenReturn(Optional.of(customer));
        when(tickets.nextTicketNumber(orgId)).thenReturn(1L);

        service.create(agentActor, new CreateTicketRequest("Subject", "Body", TicketPriority.HIGH, customer.getId()));

        ArgumentCaptor<Ticket> saved = ArgumentCaptor.forClass(Ticket.class);
        verify(tickets).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRequester()).isSameAs(customer);
        assertThat(saved.getValue().getPriority()).isEqualTo(TicketPriority.HIGH);
    }

    @Test
    void create_onBehalfOfNonCustomer_throwsInvalidRequester() {
        when(userService.findActiveCustomer(orgId, agent2.getId())).thenReturn(Optional.empty());
        assertCode(() -> service.create(agentActor, new CreateTicketRequest("Subject", "Body", null, agent2.getId())),
                ErrorCode.INVALID_REQUESTER);
        verify(tickets, never()).saveAndFlush(any());
    }

    @Test
    void create_byCustomerWithRequesterId_throws403() {
        assertCode(() -> service.create(customerActor, new CreateTicketRequest("Subject", "Body", null, otherCustomer.getId())),
                ErrorCode.ACCESS_DENIED);
        verify(tickets, never()).nextTicketNumber(any());
    }

    @Test
    void create_recordsCreatedHistory() {
        when(userService.requireActor(customerActor)).thenReturn(customer);
        when(tickets.nextTicketNumber(orgId)).thenReturn(1L);
        service.create(customerActor, new CreateTicketRequest("Subject", "Body", null, null));
        verify(historyRecorder).created(any(), any(Ticket.class));
    }

    // ───────────── assign ─────────────

    @Test
    void assign_toAgentInSameOrg_succeedsAndRecordsHistory() {
        Ticket t = stored(Fixtures.ticket(customer));
        when(userService.findAssignable(orgId, agent.getId())).thenReturn(Optional.of(agent));

        TicketDetail detail = service.assign(agentActor, t.getId(), agent.getId());

        assertThat(detail.assignee().id()).isEqualTo(agent.getId());
        verify(historyRecorder).assigneeChanged(agentActor, t, null, agent.getId());
    }

    @Test
    void assign_reassign_recordsOldAndNew() {
        Ticket t = stored(Fixtures.ticket(customer));
        t.assignTo(agent);
        when(userService.findAssignable(orgId, agent2.getId())).thenReturn(Optional.of(agent2));
        service.assign(agentActor, t.getId(), agent2.getId());
        verify(historyRecorder).assigneeChanged(agentActor, t, agent.getId(), agent2.getId());
    }

    @Test
    void assign_toCustomer_throwsInvalidAssignee() {
        Ticket t = stored(Fixtures.ticket(customer));
        when(userService.findAssignable(orgId, customer.getId())).thenReturn(Optional.empty());
        assertCode(() -> service.assign(agentActor, t.getId(), customer.getId()), ErrorCode.INVALID_ASSIGNEE);
    }

    @Test
    void assign_toInactiveAgent_throwsInvalidAssignee() {
        Ticket t = stored(Fixtures.ticket(customer));
        UUID inactive = UUID.randomUUID();
        when(userService.findAssignable(orgId, inactive)).thenReturn(Optional.empty());
        assertCode(() -> service.assign(agentActor, t.getId(), inactive), ErrorCode.INVALID_ASSIGNEE);
    }

    @Test
    void assign_toUserOfOtherOrg_throwsInvalidAssignee() {
        Ticket t = stored(Fixtures.ticket(customer));
        UUID foreignAgent = UUID.randomUUID();
        // The lookup is made with the ACTOR's org — a foreign user simply isn't found there.
        when(userService.findAssignable(orgId, foreignAgent)).thenReturn(Optional.empty());
        assertCode(() -> service.assign(agentActor, t.getId(), foreignAgent), ErrorCode.INVALID_ASSIGNEE);
        verify(userService).findAssignable(orgId, foreignAgent);
        verifyNoInteractions(historyRecorder);
    }

    @Test
    void assign_sameAssignee_isNoOpWithoutHistory() {
        Ticket t = stored(Fixtures.ticket(customer));
        t.assignTo(agent);
        when(userService.findAssignable(orgId, agent.getId())).thenReturn(Optional.of(agent));
        service.assign(agentActor, t.getId(), agent.getId());
        verifyNoInteractions(historyRecorder);
    }

    @Test
    void assign_closedTicket_throwsTicketClosed() {
        Ticket t = stored(Fixtures.ticket(customer, TicketStatus.CLOSED));
        assertCode(() -> service.assign(agentActor, t.getId(), agent.getId()), ErrorCode.TICKET_CLOSED);
    }

    @Test
    void assign_byCustomer_throws403() {
        Ticket t = stored(Fixtures.ticket(customer));
        assertCode(() -> service.assign(customerActor, t.getId(), agent.getId()), ErrorCode.ACCESS_DENIED);
    }

    @Test
    void unassign_recordsHistory_andIsNoOpWhenAlreadyUnassigned() {
        Ticket t = stored(Fixtures.ticket(customer));
        service.unassign(agentActor, t.getId());
        verifyNoInteractions(historyRecorder);

        t.assignTo(agent);
        TicketDetail detail = service.unassign(agentActor, t.getId());
        assertThat(detail.assignee()).isNull();
        verify(historyRecorder).assigneeChanged(agentActor, t, agent.getId(), null);
    }

    // ───────────── status ─────────────

    @Test
    void changeStatus_valid_recordsHistory_andOptionalComment() {
        Ticket t = stored(Fixtures.ticket(customer, TicketStatus.IN_PROGRESS));

        TicketDetail detail = service.changeStatus(agentActor, t.getId(), TicketStatus.RESOLVED, "  Fixed SMTP.  ");

        assertThat(detail.status()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(detail.resolvedAt()).isEqualTo(Fixtures.NOW);
        verify(historyRecorder).statusChanged(agentActor, t, TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED);
        verify(commentWriter).addStatusComment(agentActor, t, "Fixed SMTP.");
    }

    @Test
    void changeStatus_withoutComment_writesNoComment() {
        Ticket t = stored(Fixtures.ticket(customer, TicketStatus.OPEN));
        service.changeStatus(agentActor, t.getId(), TicketStatus.IN_PROGRESS, "   ");
        verifyNoInteractions(commentWriter);
    }

    @Test
    void changeStatus_invalid_throws409_andWritesNothing() {
        Ticket t = stored(Fixtures.ticket(customer, TicketStatus.ON_HOLD));
        assertCode(() -> service.changeStatus(agentActor, t.getId(), TicketStatus.OPEN, "comment"),
                ErrorCode.INVALID_STATUS_TRANSITION);
        verifyNoInteractions(historyRecorder);
        verifyNoInteractions(commentWriter);
        assertThat(t.getStatus()).isEqualTo(TicketStatus.ON_HOLD);
    }

    @Test
    void changeStatus_customerClosesOwnTicket_andReopensResolved() {
        Ticket t = stored(Fixtures.ticket(customer, TicketStatus.RESOLVED));
        service.changeStatus(customerActor, t.getId(), TicketStatus.OPEN, null);
        assertThat(t.getStatus()).isEqualTo(TicketStatus.OPEN);
        service.changeStatus(customerActor, t.getId(), TicketStatus.CLOSED, null);
        assertThat(t.getStatus()).isEqualTo(TicketStatus.CLOSED);
    }

    @Test
    void changeStatus_customerReopensClosed_throws403() {
        Ticket t = stored(Fixtures.ticket(customer, TicketStatus.CLOSED));
        assertCode(() -> service.changeStatus(customerActor, t.getId(), TicketStatus.OPEN, null), ErrorCode.ACCESS_DENIED);
    }

    // ───────────── update ─────────────

    @Test
    void update_customerOwnOpenTicket_canEditSubject() {
        Ticket t = stored(Fixtures.ticket(customer));
        TicketDetail detail = service.update(customerActor, t.getId(), new UpdateTicketRequest("New subject", null, null, 3L));
        assertThat(detail.subject()).isEqualTo("New subject");
        verifyNoInteractions(historyRecorder);
    }

    @Test
    void update_customerChangesPriority_throws403() {
        Ticket t = stored(Fixtures.ticket(customer));
        assertCode(() -> service.update(customerActor, t.getId(), new UpdateTicketRequest(null, null, TicketPriority.URGENT, 3L)),
                ErrorCode.ACCESS_DENIED);
    }

    @Test
    void update_customerSendsUnchangedPriority_isAllowed() {
        Ticket t = stored(Fixtures.ticket(customer));
        service.update(customerActor, t.getId(), new UpdateTicketRequest("New subject", null, TicketPriority.MEDIUM, 3L));
        assertThat(t.getSubject()).isEqualTo("New subject");
    }

    @Test
    void update_customerOnInProgressTicket_throws403() {
        Ticket t = stored(Fixtures.ticket(customer, TicketStatus.IN_PROGRESS));
        assertCode(() -> service.update(customerActor, t.getId(), new UpdateTicketRequest("New subject", null, null, 3L)),
                ErrorCode.ACCESS_DENIED);
    }

    @Test
    void update_staleVersion_throwsConcurrentModification() {
        Ticket t = stored(Fixtures.ticket(customer));
        assertCode(() -> service.update(agentActor, t.getId(), new UpdateTicketRequest("New subject", null, null, 2L)),
                ErrorCode.CONCURRENT_MODIFICATION);
    }

    @Test
    void update_priorityChange_recordsPriorityHistory() {
        Ticket t = stored(Fixtures.ticket(customer));
        service.update(agentActor, t.getId(), new UpdateTicketRequest(null, null, TicketPriority.URGENT, 3L));
        verify(historyRecorder).priorityChanged(agentActor, t, TicketPriority.MEDIUM, TicketPriority.URGENT);
        assertThat(t.getPriority()).isEqualTo(TicketPriority.URGENT);
    }

    @Test
    void update_closedTicket_throwsTicketClosed() {
        Ticket t = stored(Fixtures.ticket(customer, TicketStatus.CLOSED));
        assertCode(() -> service.update(agentActor, t.getId(), new UpdateTicketRequest("New subject", null, null, 3L)),
                ErrorCode.TICKET_CLOSED);
    }

    // ───────────── visibility ─────────────

    @Test
    void load_ticketOfOtherOrg_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(tickets.findByIdAndOrganizationId(id, orgId)).thenReturn(Optional.empty());
        assertCode(() -> service.changeStatus(agentActor, id, TicketStatus.CLOSED, null), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void load_otherCustomersTicket_throwsNotFound() {
        Ticket t = stored(Fixtures.ticket(otherCustomer));
        assertCode(() -> service.update(customerActor, t.getId(), new UpdateTicketRequest("x y z", null, null, 3L)),
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    private static void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo(code);
    }
}
