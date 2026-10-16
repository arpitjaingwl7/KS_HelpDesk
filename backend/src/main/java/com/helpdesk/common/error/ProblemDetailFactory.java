package com.helpdesk.common.error;

import com.helpdesk.common.web.RequestTracingFilter;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Builds the RFC 7807 problem body shared by the MVC exception handler and the security
 * entry points, extended with {@code code}, {@code traceId}, {@code timestamp} and {@code errors[]}.
 */
@Component
public class ProblemDetailFactory {

    public static final String TYPE_BASE = "https://helpdesk.local/problems/";

    private final Clock clock;

    public ProblemDetailFactory(Clock clock) {
        this.clock = clock;
    }

    public ProblemDetail create(ErrorCode code, String detail, String instance) {
        return create(code, detail, instance, List.of());
    }

    public ProblemDetail create(ErrorCode code, String detail, String instance, List<FieldError> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setType(URI.create(TYPE_BASE + code.slug()));
        problem.setTitle(code.title());
        if (instance != null) {
            problem.setInstance(URI.create(instance));
        }
        problem.setProperty("code", code.name());
        problem.setProperty("traceId", MDC.get(RequestTracingFilter.MDC_TRACE_ID));
        problem.setProperty("timestamp", Instant.now(clock));
        if (errors != null && !errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
        return problem;
    }
}
