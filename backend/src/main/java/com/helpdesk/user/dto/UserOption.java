package com.helpdesk.user.dto;

import com.helpdesk.security.Role;
import java.util.UUID;

/** Lightweight entry for the assignee / "on behalf of" pickers ({@code GET /agents}, {@code GET /customers}). */
public record UserOption(UUID id, String fullName, String email, Role role) {
}
