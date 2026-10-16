package com.helpdesk.common.error;

import java.util.List;
import java.util.UUID;

/**
 * The single exception type for business-rule failures. The {@link GlobalExceptionHandler}
 * turns it into an RFC 7807 problem response; the message becomes the problem {@code detail}.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient List<FieldError> fieldErrors;

    public ApiException(ErrorCode code, String detail) {
        this(code, detail, List.of());
    }

    public ApiException(ErrorCode code, String detail, List<FieldError> fieldErrors) {
        super(detail);
        this.code = code;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public ErrorCode code() {
        return code;
    }

    public List<FieldError> fieldErrors() {
        return fieldErrors;
    }

    /** 404 for anything the caller may not see — including other tenants' resources (ADR-004). */
    public static ApiException notFound(String resource, UUID id) {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, resource + " " + id + " was not found");
    }

    public static ApiException accessDenied(String detail) {
        return new ApiException(ErrorCode.ACCESS_DENIED, detail);
    }

    public static ApiException validation(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "Request has 1 invalid field",
                List.of(new FieldError(field, message, null)));
    }

    public static ApiException concurrentModification(String resource) {
        return new ApiException(ErrorCode.CONCURRENT_MODIFICATION,
                resource + " was changed by someone else. Reload and try again");
    }
}
