package com.helpdesk.organization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Only the name can change — the slug is used for login (FR-8). */
public record UpdateOrganizationRequest(
        @Schema(example = "Acme Inc") @NotBlank @Size(min = 2, max = 120) String name) {

    public UpdateOrganizationRequest {
        name = name == null ? null : name.strip();
    }
}
