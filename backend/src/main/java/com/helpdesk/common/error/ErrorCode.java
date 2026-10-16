package com.helpdesk.common.error;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes. Clients branch on these, never on message text (ADR-007).
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    INVALID_SORT(HttpStatus.BAD_REQUEST, "Invalid sort"),
    INVALID_ASSIGNEE(HttpStatus.BAD_REQUEST, "Invalid assignee"),
    INVALID_REQUESTER(HttpStatus.BAD_REQUEST, "Invalid requester"),

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),

    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),

    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),

    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),

    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "Invalid status transition"),
    TICKET_CLOSED(HttpStatus.CONFLICT, "Ticket is closed"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Concurrent modification"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email already exists"),
    SLUG_ALREADY_EXISTS(HttpStatus.CONFLICT, "Organization slug already exists"),
    LAST_ADMIN(HttpStatus.CONFLICT, "Last organization admin"),
    SELF_DEACTIVATION(HttpStatus.CONFLICT, "Self deactivation not allowed"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** {@code VALIDATION_FAILED} → {@code validation-failed}, used in the problem {@code type} URI. */
    public String slug() {
        return name().toLowerCase().replace('_', '-');
    }
}
