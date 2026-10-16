package com.helpdesk.ticket.domain;

import com.helpdesk.common.error.ApiException;
import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.ticket.repository.TicketRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The single choke point for ticket visibility and fine-grained permissions (05 §2.3).
 * Every ticket, comment and history endpoint calls {@link #loadVisible} first.
 *
 * <p>Rule of thumb (ADR-004): if the caller may not <em>see</em> the ticket → 404;
 * if they can see it but may not do the action → 403; if the ticket's state forbids it → 409.
 */
@Component
public class TicketAccessPolicy {

    private final TicketRepository tickets;

    public TicketAccessPolicy(TicketRepository tickets) {
        this.tickets = tickets;
    }

    /** Loads a ticket the actor is allowed to see, otherwise throws 404 (other org, or another customer's). */
    public Ticket loadVisible(AuthenticatedUser actor, UUID ticketId) {
        Ticket ticket = tickets.findByIdAndOrganizationId(ticketId, actor.organizationId())
                .orElseThrow(() -> ApiException.notFound("Ticket", ticketId));
        if (actor.isCustomer() && !ticket.isRequestedBy(actor.userId())) {
            throw ApiException.notFound("Ticket", ticketId);   // hide existence
        }
        return ticket;
    }

    /**
     * BR-3: customers may edit only subject and description, only while the ticket is OPEN;
     * they may never change priority. BR-6: nobody edits a CLOSED ticket.
     */
    public void requireCanEdit(AuthenticatedUser actor, Ticket ticket, boolean changesPriority) {
        if (actor.isCustomer()) {
            if (changesPriority) {
                throw ApiException.accessDenied("Customers cannot change the priority of a ticket");
            }
            if (ticket.getStatus() != TicketStatus.OPEN && !ticket.isClosed()) {
                throw ApiException.accessDenied("Customers can only edit tickets that are OPEN");
            }
        }
        ticket.ensureNotClosed();
    }

    public void requireCanAssign(AuthenticatedUser actor, Ticket ticket) {
        if (!actor.isStaff()) {
            throw ApiException.accessDenied("Only agents and admins can assign tickets");
        }
        ticket.ensureNotClosed();
    }

    /** 409 if the transition doesn't exist, 403 if it exists but this role may not make it. */
    public void requireCanTransition(AuthenticatedUser actor, Ticket ticket, TicketStatus target) {
        TicketStateMachine.validate(actor.role(), ticket.getStatus(), target);
    }

    /** FR-21: customers never post INTERNAL notes (403). BR-6: no comments on CLOSED tickets (409). */
    public void requireCanComment(AuthenticatedUser actor, Ticket ticket, CommentVisibility visibility) {
        if (visibility == CommentVisibility.INTERNAL && !actor.isStaff()) {
            throw ApiException.accessDenied("Customers cannot add internal notes");
        }
        ticket.ensureNotClosed();
    }

    /** Whether the actor may see INTERNAL comments. */
    public boolean canSeeInternalNotes(AuthenticatedUser actor) {
        return actor.isStaff();
    }

    /** Flags for the UI, mirroring the checks above. */
    public TicketPermissions permissionsFor(AuthenticatedUser actor, Ticket ticket) {
        boolean open = !ticket.isClosed();
        boolean staff = actor.isStaff();
        boolean canEdit = open && (staff || ticket.getStatus() == TicketStatus.OPEN);
        return new TicketPermissions(canEdit, open && staff, open && staff, open && staff, open);
    }
}
