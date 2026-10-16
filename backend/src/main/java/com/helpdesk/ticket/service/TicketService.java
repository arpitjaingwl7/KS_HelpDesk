package com.helpdesk.ticket.service;

import com.helpdesk.common.error.ApiException;
import com.helpdesk.common.error.ErrorCode;
import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.ticket.domain.Ticket;
import com.helpdesk.ticket.domain.TicketAccessPolicy;
import com.helpdesk.ticket.domain.TicketPriority;
import com.helpdesk.ticket.domain.TicketStatus;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.ticket.web.dto.CreateTicketRequest;
import com.helpdesk.ticket.web.dto.TicketDetail;
import com.helpdesk.ticket.web.dto.UpdateTicketRequest;
import com.helpdesk.user.User;
import com.helpdesk.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ticket commands (FR-10, FR-13, FR-14, FR-15). Each method is one transaction: the change and its
 * history entry commit together or not at all (BR-7). Every load goes through
 * {@link TicketAccessPolicy#loadVisible}, which is scoped to the actor's organization.
 */
@Service
public class TicketService {

    private final TicketRepository tickets;
    private final TicketAccessPolicy policy;
    private final UserService userService;
    private final TicketAuditTrail auditTrail;
    private final StatusChangeCommentWriter commentWriter;
    private final TicketQueryService queries;
    private final Clock clock;

    public TicketService(TicketRepository tickets, TicketAccessPolicy policy, UserService userService,
                         TicketAuditTrail auditTrail, StatusChangeCommentWriter commentWriter,
                         TicketQueryService queries, Clock clock) {
        this.tickets = tickets;
        this.policy = policy;
        this.userService = userService;
        this.auditTrail = auditTrail;
        this.commentWriter = commentWriter;
        this.queries = queries;
        this.clock = clock;
    }

    @Transactional
    public TicketDetail create(AuthenticatedUser actor, CreateTicketRequest request) {
        User requester = resolveRequester(actor, request.requesterId());
        long number = tickets.nextTicketNumber(actor.organizationId());
        Ticket ticket = Ticket.open(actor.organizationId(), number, request.subject(), request.description(),
                request.priority(), requester);
        tickets.saveAndFlush(ticket);
        auditTrail.created(actor, ticket);
        return queries.toDetail(actor, ticket);
    }

    @Transactional
    public TicketDetail update(AuthenticatedUser actor, UUID ticketId, UpdateTicketRequest request) {
        Ticket ticket = policy.loadVisible(actor, ticketId);
        if (!Objects.equals(ticket.getVersion(), request.version())) {
            throw ApiException.concurrentModification("Ticket #" + ticket.getNumber());
        }
        TicketPriority oldPriority = ticket.getPriority();
        boolean priorityChanged = request.priority() != null && request.priority() != oldPriority;
        policy.requireCanEdit(actor, ticket, priorityChanged);

        ticket.updateDetails(request.subject(), request.description(), priorityChanged ? request.priority() : null);
        if (priorityChanged) {
            auditTrail.priorityChanged(actor, ticket, oldPriority, request.priority());
        }
        tickets.flush();
        return queries.toDetail(actor, ticket);
    }

    @Transactional
    public TicketDetail assign(AuthenticatedUser actor, UUID ticketId, UUID assigneeId) {
        Ticket ticket = policy.loadVisible(actor, ticketId);
        policy.requireCanAssign(actor, ticket);
        // Looked up inside the actor's org: a user of another org is simply "not assignable" (no leak).
        User assignee = userService.findAssignable(actor.organizationId(), assigneeId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_ASSIGNEE,
                        "Assignee must be an active agent or admin of your organization"));
        UUID previous = ticket.assigneeId();
        if (assignee.getId().equals(previous)) {
            return queries.toDetail(actor, ticket);   // idempotent: no change, no history
        }
        ticket.assignTo(assignee);
        auditTrail.assigneeChanged(actor, ticket, previous, assignee.getId());
        tickets.flush();
        return queries.toDetail(actor, ticket);
    }

    @Transactional
    public TicketDetail unassign(AuthenticatedUser actor, UUID ticketId) {
        Ticket ticket = policy.loadVisible(actor, ticketId);
        policy.requireCanAssign(actor, ticket);
        UUID previous = ticket.assigneeId();
        if (previous == null) {
            return queries.toDetail(actor, ticket);
        }
        ticket.unassign();
        auditTrail.assigneeChanged(actor, ticket, previous, null);
        tickets.flush();
        return queries.toDetail(actor, ticket);
    }

    /** Close and reopen are status changes too. The optional comment is added as PUBLIC in the same transaction. */
    @Transactional
    public TicketDetail changeStatus(AuthenticatedUser actor, UUID ticketId, TicketStatus target, String comment) {
        Ticket ticket = policy.loadVisible(actor, ticketId);
        policy.requireCanTransition(actor, ticket, target);
        TicketStatus previous = ticket.getStatus();
        ticket.changeStatus(target, Instant.now(clock));
        auditTrail.statusChanged(actor, ticket, previous, target);
        if (comment != null && !comment.isBlank()) {
            commentWriter.addStatusComment(actor, ticket, comment.strip());
        }
        tickets.flush();
        return queries.toDetail(actor, ticket);
    }

    /**
     * The requester is the actor, unless staff raise the ticket on behalf of an active customer of
     * their own organization. A customer sending {@code requesterId} → 403.
     */
    private User resolveRequester(AuthenticatedUser actor, UUID requesterId) {
        if (requesterId == null || requesterId.equals(actor.userId())) {
            return userService.requireActor(actor);
        }
        if (!actor.isStaff()) {
            throw ApiException.accessDenied("Customers cannot raise tickets on behalf of someone else");
        }
        return userService.findActiveCustomer(actor.organizationId(), requesterId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUESTER,
                        "Requester must be an active customer of your organization"));
    }
}
