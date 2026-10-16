package com.helpdesk.ticket.web.dto;

import java.util.UUID;

/** Requester / assignee as shown on the ticket detail. */
public record TicketUser(UUID id, String fullName, String email) {
}
