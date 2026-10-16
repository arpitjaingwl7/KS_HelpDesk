package com.helpdesk.ticket.web.dto;

import com.helpdesk.ticket.domain.TicketPermissions;
import com.helpdesk.ticket.domain.TicketPriority;
import com.helpdesk.ticket.domain.TicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TicketDetail(
        UUID id,
        long number,
        String subject,
        String description,
        TicketStatus status,
        TicketPriority priority,
        TicketUser requester,
        TicketUser assignee,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        Instant closedAt,
        @Schema(description = "Send back on PATCH for optimistic locking") long version,
        @Schema(description = "Statuses this caller may move the ticket to") List<TicketStatus> allowedTransitions,
        @Schema(description = "What this caller may do with the ticket") TicketPermissions permissions) {
}
