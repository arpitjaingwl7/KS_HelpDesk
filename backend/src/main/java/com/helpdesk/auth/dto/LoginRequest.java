package com.helpdesk.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(example = "acme") @NotBlank @Size(max = 50) String organizationSlug,
        @Schema(example = "admin@acme.test") @NotBlank @Size(max = 254) String email,
        @Schema(example = "Password123!") @NotBlank @Size(max = 72) String password) {

    public LoginRequest {
        organizationSlug = organizationSlug == null ? null : organizationSlug.strip().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public String toString() {
        return "LoginRequest[organizationSlug=" + organizationSlug + ", email=" + email + ", password=***]";
    }
}
