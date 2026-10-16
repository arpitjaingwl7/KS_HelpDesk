package com.helpdesk.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.common.error.ErrorCode;
import com.helpdesk.common.error.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Authenticated but the role does not allow the path → {@code 403} as a ProblemDetail. */
@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailFactory problems;
    private final ObjectMapper objectMapper;

    public ProblemAccessDeniedHandler(ProblemDetailFactory problems, ObjectMapper objectMapper) {
        this.problems = problems;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problems.create(ErrorCode.ACCESS_DENIED,
                "You do not have permission to perform this action", request.getRequestURI()));
    }
}
