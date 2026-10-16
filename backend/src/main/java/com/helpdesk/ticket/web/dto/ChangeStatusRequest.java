package com.helpdesk.ticket.web.dto;

import com.helpdesk.ticket.domain.TicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeStatusRequest(
        @Schema(example = "RESOLVED") @NotNull TicketStatus status,
        @Schema(description = "Optional; added as a PUBLIC comment in the same transaction", example = "Fixed SMTP configuration.")
        @Size(max = 5_000) String comment) {
}
