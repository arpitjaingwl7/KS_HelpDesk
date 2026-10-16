package com.helpdesk.security;

import java.util.UUID;

/**
 * The trusted identity of the caller, built on every request from the verified JWT <em>and</em>
 * the database (the role is the database value, so changes apply immediately).
 *
 * <p>Services receive it as an explicit {@code actor} parameter — there is no hidden ThreadLocal
 * tenant (ADR-008). {@link #organizationId()} is the only source of the tenant (BR-1).
 */
public record AuthenticatedUser(UUID userId, UUID organizationId, Role role, String email) {

    public boolean isCustomer() {
        return role == Role.CUSTOMER;
    }

    public boolean isStaff() {
        return role.isStaff();
    }

    public boolean isAdmin() {
        return role == Role.ORG_ADMIN;
    }
}
