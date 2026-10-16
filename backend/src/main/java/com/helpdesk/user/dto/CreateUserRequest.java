package com.helpdesk.user.dto;

import com.helpdesk.security.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @Schema(example = "Bob Agent") @NotBlank @Size(max = 120) String fullName,
        @Schema(example = "bob@acme.com") @NotBlank @Email @Size(max = 254) String email,
        @Schema(example = "Initial#123") @NotBlank @Size(min = 8, max = 72) String password,
        @Schema(example = "AGENT") @NotNull Role role) {

    public CreateUserRequest {
        fullName = fullName == null ? null : fullName.strip();
        email = email == null ? null : email.strip();
    }
}
