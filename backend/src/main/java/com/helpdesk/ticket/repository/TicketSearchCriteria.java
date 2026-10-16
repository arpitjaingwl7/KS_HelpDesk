package com.helpdesk.ticket.repository;

import com.helpdesk.ticket.domain.TicketPriority;
import com.helpdesk.ticket.domain.TicketStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Optional ticket filters (FR-16, FR-17). Values combine with AND; multiple values of one filter
 * combine with OR. Never contains an organization id — that always comes from the actor.
 */
public record TicketSearchCriteria(
        String q,
        List<TicketStatus> statuses,
        List<TicketPriority> priorities,
        UUID assigneeId,
        boolean unassigned,
        UUID requesterId,
        Instant createdFrom,
        Instant createdTo) {

    public static TicketSearchCriteria none() {
        return new TicketSearchCriteria(null, List.of(), List.of(), null, false, null, null, null);
    }

    public TicketSearchCriteria {
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
        priorities = priorities == null ? List.of() : List.copyOf(priorities);
    }
}
