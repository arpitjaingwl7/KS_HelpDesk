package com.helpdesk.user.dto;

import com.helpdesk.security.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Missing fields mean "no change". {@code version} is required for optimistic locking. */
public record UpdateUserRequest(
        @Schema(example = "Bob B. Agent") @Size(min = 1, max = 120) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String fullName,
        @Schema(example = "ORG_ADMIN") Role role,
        @Schema(example = "true") Boolean active,
        @Schema(example = "0") @NotNull Long version) {

    public UpdateUserRequest {
        fullName = fullName == null ? null : fullName.strip();
    }
}
