package com.helpdesk.auth.dto;

import com.helpdesk.organization.dto.OrganizationRef;
import com.helpdesk.security.Role;
import java.util.UUID;

/** The caller and their organization — returned by login, registration and {@code GET /auth/me}. */
public record CurrentUserResponse(UUID id, String fullName, String email, Role role, OrganizationRef organization) {
}
