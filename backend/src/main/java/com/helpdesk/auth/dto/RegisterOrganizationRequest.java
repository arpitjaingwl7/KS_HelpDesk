package com.helpdesk.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterOrganizationRequest(
        @Schema(example = "Acme Corp") @NotBlank @Size(min = 2, max = 120) String organizationName,
        @Schema(example = "acme", description = "Lowercase letters, digits and dashes; 3–50 chars; used at login")
        @NotBlank
        @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$",
                message = "must be 3-50 lowercase letters, digits or dashes, and must not start or end with a dash")
        String organizationSlug,
        @Schema(example = "Alice Admin") @NotBlank @Size(max = 120) String adminFullName,
        @Schema(example = "alice@acme.com") @NotBlank @Email @Size(max = 254) String adminEmail,
        @Schema(example = "S3cure!pass") @NotBlank @Size(min = 8, max = 72) String adminPassword) {

    public RegisterOrganizationRequest {
        organizationName = organizationName == null ? null : organizationName.strip();
        organizationSlug = organizationSlug == null ? null : organizationSlug.strip();
        adminFullName = adminFullName == null ? null : adminFullName.strip();
        adminEmail = adminEmail == null ? null : adminEmail.strip();
    }

    @Override
    public String toString() {
        return "RegisterOrganizationRequest[organizationSlug=" + organizationSlug + ", adminEmail=" + adminEmail + ", adminPassword=***]";
    }
}
