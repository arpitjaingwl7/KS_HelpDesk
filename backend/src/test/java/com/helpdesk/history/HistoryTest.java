package com.helpdesk.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.helpdesk.history.dto.HistoryEntryResponse;
import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.security.Role;
import com.helpdesk.support.Fixtures;
import com.helpdesk.ticket.domain.Ticket;
import com.helpdesk.ticket.domain.TicketAccessPolicy;
import com.helpdesk.ticket.domain.TicketPriority;
import com.helpdesk.ticket.domain.TicketStatus;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.user.User;
import com.helpdesk.user.UserService;
import com.helpdesk.user.dto.UserRef;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HistoryTest {

    @Mock
    TicketHistoryRepository repository;
    @Mock
    TicketRepository tickets;
    @Mock
    UserService userService;

    final UUID orgId = UUID.randomUUID();
    final User customer = Fixtures.user(orgId, Role.CUSTOMER);
    final User agent = Fixtures.user(orgId, Role.AGENT);
    final AuthenticatedUser agentActor = Fixtures.actor(agent);

    @Test
    void recorder_writesEntriesWithActorOrgAndValues() {
        TicketHistoryRecorder recorder = new TicketHistoryRecorder(repository);
        Ticket t = Fixtures.ticket(customer);

        recorder.created(agentActor, t);
        recorder.statusChanged(agentActor, t, TicketStatus.OPEN, TicketStatus.IN_PROGRESS);
        recorder.assigneeChanged(agentActor, t, null, agent.getId());
        recorder.priorityChanged(agentActor, t, TicketPriority.LOW, TicketPriority.HIGH);

        ArgumentCaptor<TicketHistory> saved = ArgumentCaptor.forClass(TicketHistory.class);
        verify(repository, org.mockito.Mockito.times(4)).save(saved.capture());
        List<TicketHistory> entries = saved.getAllValues();
        assertThat(entries).extracting(TicketHistory::getEventType).containsExactly(HistoryEventType.CREATED,
                HistoryEventType.STATUS_CHANGED, HistoryEventType.ASSIGNEE_CHANGED, HistoryEventType.PRIORITY_CHANGED);
        assertThat(entries).allSatisfy(e -> {
            assertThat(e.getOrganizationId()).isEqualTo(orgId);
            assertThat(e.getTicketId()).isEqualTo(t.getId());
            assertThat(e.getActorId()).isEqualTo(agent.getId());
        });
        assertThat(entries.get(0).getNewValue()).isEqualTo("OPEN");
        assertThat(entries.get(2).getOldValue()).isNull();
        assertThat(entries.get(2).getNewValue()).isEqualTo(agent.getId().toString());
        assertThat(entries.get(3).getOldValue()).isEqualTo("LOW");
    }

    @Test
    void service_resolvesUserNamesInOneBatch() {
        Ticket t = Fixtures.ticket(customer);
        when(tickets.findByIdAndOrganizationId(t.getId(), orgId)).thenReturn(Optional.of(t));
        UUID unknown = UUID.randomUUID();
        TicketHistory created = new TicketHistory(orgId, t.getId(), customer.getId(), HistoryEventType.CREATED, null, "OPEN");
        TicketHistory assigned = new TicketHistory(orgId, t.getId(), agent.getId(), HistoryEventType.ASSIGNEE_CHANGED,
                unknown.toString(), agent.getId().toString());
        when(repository.findByOrganizationIdAndTicketId(eq(orgId), eq(t.getId()), any())).thenReturn(List.of(created, assigned));
        when(userService.refsById(orgId, Set.of(customer.getId(), agent.getId(), unknown))).thenReturn(Map.of(
                customer.getId(), new UserRef(customer.getId(), customer.getFullName()),
                agent.getId(), new UserRef(agent.getId(), agent.getFullName())));

        List<HistoryEntryResponse> result = new HistoryService(repository, new TicketAccessPolicy(tickets), userService)
                .list(agentActor, t.getId());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).newValue()).isEqualTo("OPEN");
        assertThat(result.get(0).actor().fullName()).isEqualTo(customer.getFullName());
        assertThat(result.get(1).newValue()).isEqualTo(new UserRef(agent.getId(), agent.getFullName()));
        assertThat(result.get(1).oldValue()).isEqualTo(new UserRef(unknown, "Unknown user"));
    }
}
