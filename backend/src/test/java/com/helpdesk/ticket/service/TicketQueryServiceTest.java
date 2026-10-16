package com.helpdesk.ticket.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.helpdesk.common.error.ApiException;
import com.helpdesk.common.error.ErrorCode;
import com.helpdesk.common.web.PageResponse;
import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.security.Role;
import com.helpdesk.support.Fixtures;
import com.helpdesk.ticket.domain.Ticket;
import com.helpdesk.ticket.domain.TicketAccessPolicy;
import com.helpdesk.ticket.domain.TicketPriority;
import com.helpdesk.ticket.domain.TicketStatus;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.ticket.repository.TicketSearchCriteria;
import com.helpdesk.ticket.web.TicketMapperImpl;
import com.helpdesk.ticket.web.dto.TicketDetail;
import com.helpdesk.ticket.web.dto.TicketStats;
import com.helpdesk.ticket.web.dto.TicketSummary;
import com.helpdesk.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class TicketQueryServiceTest {

    @Mock
    TicketRepository tickets;

    TicketQueryService service;

    final UUID orgId = UUID.randomUUID();
    final User customer = Fixtures.user(orgId, Role.CUSTOMER);
    final User agent = Fixtures.user(orgId, Role.AGENT);

    @BeforeEach
    void setUp() {
        service = new TicketQueryService(tickets, new TicketAccessPolicy(tickets), new TicketMapperImpl());
    }

    @Test
    void get_returnsPermissionsAndTransitionsForCaller() {
        Ticket t = Fixtures.ticket(customer, TicketStatus.RESOLVED);
        when(tickets.findByIdAndOrganizationId(t.getId(), orgId)).thenReturn(Optional.of(t));

        TicketDetail asCustomer = service.get(Fixtures.actor(customer), t.getId());
        TicketDetail asAgent = service.get(Fixtures.actor(agent), t.getId());

        assertThat(asCustomer.allowedTransitions()).containsExactly(TicketStatus.OPEN, TicketStatus.CLOSED);
        assertThat(asCustomer.permissions().canAddInternalNote()).isFalse();
        assertThat(asAgent.permissions().canAddInternalNote()).isTrue();
        assertThat(asAgent.requester().email()).isEqualTo(customer.getEmail());
        assertThat(asAgent.version()).isEqualTo(3L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_mapsSortAndReturnsPageMetadata() {
        Ticket t = Fixtures.ticket(customer);
        when(tickets.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(t), PageRequest.of(0, 20), 1));

        PageResponse<TicketSummary> page = service.search(Fixtures.actor(agent), TicketSearchCriteria.none(),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "priority")));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(tickets).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("priorityRank")).isNotNull();
        assertThat(page.page().totalElements()).isEqualTo(1);
        assertThat(page.content().get(0).requester().fullName()).isEqualTo(customer.getFullName());
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_defaultSortIsCreatedAtDesc() {
        when(tickets.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        service.search(Fixtures.actor(agent), TicketSearchCriteria.none(), PageRequest.of(0, 20));
        verify(tickets).findAll(any(Specification.class), eq(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @Test
    void search_unknownSortField_throwsInvalidSort() {
        assertThatThrownBy(() -> service.search(Fixtures.actor(agent), TicketSearchCriteria.none(),
                PageRequest.of(0, 20, Sort.by("subject"))))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.INVALID_SORT);
    }

    @Test
    void search_assigneeAndUnassignedTogether_throwsValidation() {
        TicketSearchCriteria criteria = new TicketSearchCriteria(null, null, null, UUID.randomUUID(), true, null, null, null);
        assertThatThrownBy(() -> service.search(Fixtures.actor(agent), criteria, PageRequest.of(0, 20)))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void stats_fillsZerosAndTotals() {
        when(tickets.countByStatus(orgId)).thenReturn(List.<Object[]>of(new Object[]{TicketStatus.OPEN, 3L},
                new Object[]{TicketStatus.CLOSED, 2L}));
        when(tickets.countOpenByPriority(orgId)).thenReturn(List.<Object[]>of(new Object[]{TicketPriority.HIGH, 3L}));
        when(tickets.countUnassignedNotClosed(orgId)).thenReturn(1L);

        TicketStats stats = service.stats(Fixtures.actor(agent));

        assertThat(stats.total()).isEqualTo(5);
        assertThat(stats.unassigned()).isEqualTo(1);
        assertThat(stats.byStatus()).containsEntry(TicketStatus.IN_PROGRESS, 0L).containsEntry(TicketStatus.OPEN, 3L);
        assertThat(stats.byPriority()).containsEntry(TicketPriority.HIGH, 3L).containsEntry(TicketPriority.LOW, 0L);
    }

    @Test
    void stats_customer_throws403() {
        AuthenticatedUser actor = Fixtures.actor(customer);
        assertThatThrownBy(() -> service.stats(actor))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.ACCESS_DENIED);
    }
}
