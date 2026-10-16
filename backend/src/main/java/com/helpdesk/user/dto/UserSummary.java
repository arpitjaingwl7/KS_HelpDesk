package com.helpdesk.user.dto;

import com.helpdesk.security.Role;
import java.time.Instant;
import java.util.UUID;

public record UserSummary(UUID id, String fullName, String email, Role role, boolean active, Instant createdAt) {
}
