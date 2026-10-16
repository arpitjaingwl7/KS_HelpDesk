package com.helpdesk.history;

import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.ticket.domain.Ticket;
import com.helpdesk.ticket.domain.TicketPriority;
import com.helpdesk.ticket.domain.TicketStatus;
import com.helpdesk.ticket.service.TicketAuditTrail;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes history entries synchronously in the caller's transaction (ADR-009). {@code MANDATORY}
 * makes it impossible to record history outside the transaction that makes the change (BR-7).
 * This is also the natural place to publish domain events once notifications arrive (Phase 9).
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class TicketHistoryRecorder implements TicketAuditTrail {

    private final TicketHistoryRepository history;

    public TicketHistoryRecorder(TicketHistoryRepository history) {
        this.history = history;
    }

    @Override
    public void created(AuthenticatedUser actor, Ticket ticket) {
        write(actor, ticket, HistoryEventType.CREATED, null, ticket.getStatus().name());
    }

    @Override
    public void statusChanged(AuthenticatedUser actor, Ticket ticket, TicketStatus from, TicketStatus to) {
        write(actor, ticket, HistoryEventType.STATUS_CHANGED, from.name(), to.name());
    }

    @Override
    public void assigneeChanged(AuthenticatedUser actor, Ticket ticket, UUID fromUserId, UUID toUserId) {
        write(actor, ticket, HistoryEventType.ASSIGNEE_CHANGED, str(fromUserId), str(toUserId));
    }

    @Override
    public void priorityChanged(AuthenticatedUser actor, Ticket ticket, TicketPriority from, TicketPriority to) {
        write(actor, ticket, HistoryEventType.PRIORITY_CHANGED, from.name(), to.name());
    }

    private void write(AuthenticatedUser actor, Ticket ticket, HistoryEventType type, String oldValue, String newValue) {
        history.save(new TicketHistory(actor.organizationId(), ticket.getId(), actor.userId(), type, oldValue, newValue));
    }

    private static String str(UUID id) {
        return id == null ? null : id.toString();
    }
}
