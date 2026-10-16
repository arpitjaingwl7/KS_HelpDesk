package com.helpdesk.ticket.web.dto;

import com.helpdesk.ticket.domain.TicketPriority;
import com.helpdesk.ticket.domain.TicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** Dashboard counters for the caller's organization (stretch endpoint #22). */
public record TicketStats(
        @Schema(description = "All tickets") long total,
        @Schema(description = "Not closed and without an assignee") long unassigned,
        @Schema(description = "All tickets by status") Map<TicketStatus, Long> byStatus,
        @Schema(description = "Not-closed tickets by priority") Map<TicketPriority, Long> byPriority) {
}
