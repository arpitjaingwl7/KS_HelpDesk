package com.helpdesk.ticket.web.dto;

import com.helpdesk.ticket.domain.TicketPriority;
import com.helpdesk.ticket.domain.TicketStatus;
import com.helpdesk.user.dto.UserRef;
import java.time.Instant;
import java.util.UUID;

public record TicketSummary(UUID id, long number, String subject, TicketStatus status, TicketPriority priority,
                            UserRef requester, UserRef assignee, Instant createdAt, Instant updatedAt) {
}
