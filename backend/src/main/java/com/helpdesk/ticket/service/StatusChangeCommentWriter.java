package com.helpdesk.ticket.service;

import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.ticket.domain.Ticket;

/**
 * Port used by {@link TicketService#changeStatus} to add the optional PUBLIC comment in the same
 * transaction as the status change. Implemented by {@code comment.CommentService} (ADR-010).
 */
public interface StatusChangeCommentWriter {

    void addStatusComment(AuthenticatedUser actor, Ticket ticket, String body);
}
