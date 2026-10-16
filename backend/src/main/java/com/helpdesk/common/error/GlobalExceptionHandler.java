package com.helpdesk.common.error;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every exception into an RFC 7807 problem response. Unexpected errors become a generic
 * {@code 500}; the stack trace goes only to the log, never to the client (T9).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemDetailFactory problems;

    public GlobalExceptionHandler(ProblemDetailFactory problems) {
        this.problems = problems;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex, HttpServletRequest request) {
        return respond(ex.code(), ex.getMessage(), request, ex.fieldErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage(), safeRejectedValue(fe.getField(), fe.getRejectedValue())))
                .collect(Collectors.toList());
        ex.getBindingResult().getGlobalErrors().forEach(ge ->
                errors.add(new FieldError(ge.getObjectName(), ge.getDefaultMessage(), null)));
        return respond(ErrorCode.VALIDATION_FAILED, invalidFieldsDetail(errors.size()), request, errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> handleParamValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        List<FieldError> errors = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream().map(e -> new FieldError(
                        r.getMethodParameter().getParameterName(), e.getDefaultMessage(), r.getArgument())))
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, invalidFieldsDetail(errors.size()), request, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldError> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldError(v.getPropertyPath().toString(), v.getMessage(), null))
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, invalidFieldsDetail(errors.size()), request, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        List<FieldError> errors = List.of();
        String detail = "Request body is missing or is not valid JSON";
        if (ex.getCause() instanceof InvalidFormatException ife && ife.getTargetType() != null && ife.getTargetType().isEnum()) {
            String allowed = Arrays.stream(ife.getTargetType().getEnumConstants()).map(Object::toString)
                    .collect(Collectors.joining(", "));
            errors = List.of(new FieldError(path(ife), "must be one of " + allowed, String.valueOf(ife.getValue())));
            detail = "Request has an invalid enum value";
        } else if (ex.getCause() instanceof MismatchedInputException mie && !mie.getPath().isEmpty()) {
            errors = List.of(new FieldError(path(mie), "has an invalid type", null));
            detail = "Request has a field with an invalid type";
        }
        return respond(ErrorCode.MALFORMED_REQUEST, detail, request, errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "has an invalid value";
        Class<?> type = ex.getRequiredType();
        if (type != null && type.isEnum()) {
            message = "must be one of " + Arrays.stream(type.getEnumConstants()).map(Object::toString)
                    .collect(Collectors.joining(", "));
        } else if (type != null && type.isArray() && type.getComponentType().isEnum()) {
            message = "must be one of " + Arrays.stream(type.getComponentType().getEnumConstants()).map(Object::toString)
                    .collect(Collectors.joining(", "));
        }
        return respond(ErrorCode.MALFORMED_REQUEST, "Parameter '" + ex.getName() + "' " + message, request,
                List.of(new FieldError(ex.getName(), message, String.valueOf(ex.getValue()))));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ProblemDetail> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return respond(ErrorCode.VALIDATION_FAILED, "Parameter '" + ex.getParameterName() + "' is required", request,
                List.of(new FieldError(ex.getParameterName(), "is required", null)));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Content type must be application/json", request, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, "Method " + ex.getMethod() + " is not supported here", request, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return respond(ErrorCode.RESOURCE_NOT_FOUND, "No endpoint " + request.getRequestURI(), request, List.of());
    }

    /** Thrown by {@code @PreAuthorize}: the caller is authenticated but lacks the role. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(ErrorCode.ACCESS_DENIED, "You do not have permission to perform this action", request, List.of());
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, jakarta.persistence.OptimisticLockException.class})
    ResponseEntity<ProblemDetail> handleOptimisticLock(RuntimeException ex, HttpServletRequest request) {
        return respond(ErrorCode.CONCURRENT_MODIFICATION,
                "The resource was changed by someone else. Reload and try again", request, List.of());
    }

    /** Unique constraint races that slipped past the service-level pre-checks. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String message = String.valueOf(ex.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        if (message.contains("uq_users_org_email")) {
            return respond(ErrorCode.EMAIL_ALREADY_EXISTS, "A user with this email already exists in the organization", request, List.of());
        }
        if (message.contains("uq_organizations_slug")) {
            return respond(ErrorCode.SLUG_ALREADY_EXISTS, "This organization slug is already taken", request, List.of());
        }
        log.error("Data integrity violation on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request, List.of());
    }

    private ResponseEntity<ProblemDetail> respond(ErrorCode code, String detail, HttpServletRequest request,
                                                  List<FieldError> errors) {
        ProblemDetail body = problems.create(code, detail, request.getRequestURI(), errors);
        return ResponseEntity.status(code.status()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    private static String invalidFieldsDetail(int count) {
        return "Request has " + count + " invalid field" + (count == 1 ? "" : "s");
    }

    /** Never echo secrets back to the client. */
    private static Object safeRejectedValue(String field, Object value) {
        return field != null && field.toLowerCase(Locale.ROOT).contains("password") ? null : value;
    }

    private static String path(MismatchedInputException ex) {
        return ex.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining("."));
    }
}
