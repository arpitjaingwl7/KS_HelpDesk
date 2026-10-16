package com.helpdesk.security;

/**
 * The three roles of the system (FR-2). Lives in {@code security} (not {@code user}) because the
 * authenticated principal carries it and every feature depends on the principal; this keeps the
 * feature packages free of dependency cycles (see ADR-010).
 */
public enum Role {
    ORG_ADMIN,
    AGENT,
    CUSTOMER;

    public String authority() {
        return "ROLE_" + name();
    }

    public boolean isStaff() {
        return this == ORG_ADMIN || this == AGENT;
    }
}
