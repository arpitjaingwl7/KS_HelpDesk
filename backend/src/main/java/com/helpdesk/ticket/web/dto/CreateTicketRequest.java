package com.helpdesk.ticket.web.dto;

import com.helpdesk.ticket.domain.TicketPriority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * No organization, status or number fields: those are never taken from the client (T2, T5).
 */
public record CreateTicketRequest(
        @Schema(example = "Cannot reset password") @NotBlank @Size(min = 3, max = 200) String subject,
        @Schema(example = "The reset email never arrives.") @NotBlank @Size(max = 10_000) String description,
        @Schema(description = "Defaults to MEDIUM", example = "HIGH") TicketPriority priority,
        @Schema(description = "Agents and admins only: raise the ticket on behalf of an active customer") UUID requesterId) {

    public CreateTicketRequest {
        subject = subject == null ? null : subject.strip();
    }
}
