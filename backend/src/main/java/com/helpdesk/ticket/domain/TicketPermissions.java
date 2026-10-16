package com.helpdesk.ticket.domain;

/**
 * What the caller may do with a ticket, computed by {@link TicketAccessPolicy}. The UI shows or hides
 * actions from these flags, so authorization rules are never duplicated in the frontend.
 */
public record TicketPermissions(boolean canEdit, boolean canChangePriority, boolean canAssign,
                                boolean canAddInternalNote, boolean canComment) {
}
