package com.helpdesk.ticket.service;

import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.ticket.domain.Ticket;
import com.helpdesk.ticket.domain.TicketPriority;
import com.helpdesk.ticket.domain.TicketStatus;
import java.util.UUID;

/**
 * Port through which {@link TicketService} records history (FR-22, BR-7). Implemented by
 * {@code history.TicketHistoryRecorder}; calls are synchronous and join the caller's transaction,
 * so history is always consistent with the change it records (ADR-009). The interface lives here so
 * {@code ticket} does not depend on {@code history} (ADR-010).
 */
public interface TicketAuditTrail {

    void created(AuthenticatedUser actor, Ticket ticket);

    void statusChanged(AuthenticatedUser actor, Ticket ticket, TicketStatus from, TicketStatus to);

    void assigneeChanged(AuthenticatedUser actor, Ticket ticket, UUID fromUserId, UUID toUserId);

    void priorityChanged(AuthenticatedUser actor, Ticket ticket, TicketPriority from, TicketPriority to);
}
