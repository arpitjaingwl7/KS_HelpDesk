package com.helpdesk.comment;

import com.helpdesk.comment.dto.CommentResponse;
import com.helpdesk.comment.dto.CreateCommentRequest;
import com.helpdesk.common.web.PageResponse;
import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.ticket.domain.CommentVisibility;
import com.helpdesk.ticket.domain.Ticket;
import com.helpdesk.ticket.domain.TicketAccessPolicy;
import com.helpdesk.ticket.service.StatusChangeCommentWriter;
import com.helpdesk.user.User;
import com.helpdesk.user.UserService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conversation thread (FR-20, FR-21). Customers never receive INTERNAL notes from any endpoint:
 * the filter is applied in the repository query, not in memory.
 */
@Service
public class CommentService implements StatusChangeCommentWriter {

    /** Oldest first; the id (UUIDv7, time-ordered) breaks ties within the same timestamp. */
    static final Sort THREAD_ORDER = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));

    private final CommentRepository comments;
    private final TicketAccessPolicy policy;
    private final UserService userService;
    private final CommentMapper mapper;

    public CommentService(CommentRepository comments, TicketAccessPolicy policy, UserService userService,
                          CommentMapper mapper) {
        this.comments = comments;
        this.policy = policy;
        this.userService = userService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> list(AuthenticatedUser actor, UUID ticketId, Pageable pageable) {
        Ticket ticket = policy.loadVisible(actor, ticketId);
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), THREAD_ORDER);
        Page<TicketComment> result = policy.canSeeInternalNotes(actor)
                ? comments.findByOrganizationIdAndTicketId(actor.organizationId(), ticket.getId(), page)
                : comments.findByOrganizationIdAndTicketIdAndVisibility(actor.organizationId(), ticket.getId(),
                        CommentVisibility.PUBLIC, page);
        return PageResponse.of(result, mapper::toResponse);
    }

    @Transactional
    public CommentResponse add(AuthenticatedUser actor, UUID ticketId, CreateCommentRequest request) {
        Ticket ticket = policy.loadVisible(actor, ticketId);
        CommentVisibility visibility = request.visibilityOrDefault();
        policy.requireCanComment(actor, ticket, visibility);
        return mapper.toResponse(save(actor, ticket, request.body().strip(), visibility));
    }

    /** Called by {@code TicketService.changeStatus} inside its transaction; the ticket is already visible. */
    @Override
    public void addStatusComment(AuthenticatedUser actor, Ticket ticket, String body) {
        save(actor, ticket, body, CommentVisibility.PUBLIC);
    }

    private TicketComment save(AuthenticatedUser actor, Ticket ticket, String body, CommentVisibility visibility) {
        User author = userService.requireActor(actor);
        return comments.saveAndFlush(new TicketComment(actor.organizationId(), ticket.getId(), author, body, visibility));
    }
}
