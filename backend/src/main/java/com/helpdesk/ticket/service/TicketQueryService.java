package com.helpdesk.ticket.service;

import com.helpdesk.common.error.ApiException;
import com.helpdesk.common.web.PageResponse;
import com.helpdesk.common.web.SortSanitizer;
import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.ticket.domain.Ticket;
import com.helpdesk.ticket.domain.TicketAccessPolicy;
import com.helpdesk.ticket.domain.TicketPriority;
import com.helpdesk.ticket.domain.TicketStateMachine;
import com.helpdesk.ticket.domain.TicketStatus;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.ticket.repository.TicketSearchCriteria;
import com.helpdesk.ticket.repository.TicketSpecifications;
import com.helpdesk.ticket.web.TicketMapper;
import com.helpdesk.ticket.web.dto.TicketDetail;
import com.helpdesk.ticket.web.dto.TicketStats;
import com.helpdesk.ticket.web.dto.TicketSummary;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ticket reads: detail (with caller-specific permissions) and search (FR-12, FR-16–FR-19). */
@Service
public class TicketQueryService {

    /** API sort name → entity property. {@code priority} sorts in business order via the generated rank. */
    static final Map<String, String> SORTABLE = Map.of(
            "createdAt", "createdAt",
            "updatedAt", "updatedAt",
            "number", "number",
            "priority", "priorityRank",
            "status", "status");
    static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final TicketRepository tickets;
    private final TicketAccessPolicy policy;
    private final TicketMapper mapper;

    public TicketQueryService(TicketRepository tickets, TicketAccessPolicy policy, TicketMapper mapper) {
        this.tickets = tickets;
        this.policy = policy;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public TicketDetail get(AuthenticatedUser actor, UUID ticketId) {
        return toDetail(actor, policy.loadVisible(actor, ticketId));
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketSummary> search(AuthenticatedUser actor, TicketSearchCriteria criteria, Pageable pageable) {
        if (criteria.unassigned() && criteria.assigneeId() != null) {
            throw ApiException.validation("unassigned", "cannot be combined with assigneeId");
        }
        Pageable page = SortSanitizer.sanitize(pageable, SORTABLE, DEFAULT_SORT);
        return PageResponse.of(tickets.findAll(TicketSpecifications.search(actor, criteria), page), mapper::toSummary);
    }

    @Transactional(readOnly = true)
    public TicketStats stats(AuthenticatedUser actor) {
        if (!actor.isStaff()) {
            throw ApiException.accessDenied("Only agents and admins can see ticket statistics");
        }
        Map<TicketStatus, Long> byStatus = new EnumMap<>(TicketStatus.class);
        for (TicketStatus s : TicketStatus.values()) {
            byStatus.put(s, 0L);
        }
        for (Object[] row : tickets.countByStatus(actor.organizationId())) {
            byStatus.put((TicketStatus) row[0], (Long) row[1]);
        }
        Map<TicketPriority, Long> byPriority = new EnumMap<>(TicketPriority.class);
        for (TicketPriority p : TicketPriority.values()) {
            byPriority.put(p, 0L);
        }
        for (Object[] row : tickets.countOpenByPriority(actor.organizationId())) {
            byPriority.put((TicketPriority) row[0], (Long) row[1]);
        }
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        return new TicketStats(total, tickets.countUnassignedNotClosed(actor.organizationId()), byStatus, byPriority);
    }

    /** Detail DTO with {@code allowedTransitions} and {@code permissions} computed for this caller. */
    TicketDetail toDetail(AuthenticatedUser actor, Ticket ticket) {
        return mapper.toDetail(ticket,
                TicketStateMachine.allowedTargets(actor.role(), ticket.getStatus()),
                policy.permissionsFor(actor, ticket));
    }
}
