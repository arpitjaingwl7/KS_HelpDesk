package com.helpdesk.ticket.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.helpdesk.ticket.domain.TicketPriority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Missing fields mean "no change". At least one field plus {@code version} is required. */
public record UpdateTicketRequest(
        @Schema(example = "Password reset email not received") @Size(min = 3, max = 200) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String subject,
        @Size(min = 1, max = 10_000) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String description,
        @Schema(example = "URGENT") TicketPriority priority,
        @Schema(example = "3") @NotNull Long version) {

    public UpdateTicketRequest {
        subject = subject == null ? null : subject.strip();
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "at least one of subject, description or priority is required")
    public boolean isAnyFieldPresent() {
        return subject != null || description != null || priority != null;
    }
}
