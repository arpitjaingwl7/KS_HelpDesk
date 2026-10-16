package com.helpdesk.ticket.domain;

/**
 * PUBLIC replies are visible to the requester; INTERNAL notes only to staff (FR-21).
 * Defined in the ticket domain because {@link TicketAccessPolicy} decides who may post which.
 */
public enum CommentVisibility {
    PUBLIC, INTERNAL
}
