package com.helpdesk.security;

import com.helpdesk.common.web.RequestTracingFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs right after bearer-token authentication and adds {@code orgId} / {@code userId} to the
 * MDC so every log line of the request carries them (NFR-7). Cleared by {@link RequestTracingFilter}.
 */
public class MdcUserFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            MDC.put(RequestTracingFilter.MDC_ORG_ID, user.organizationId().toString());
            MDC.put(RequestTracingFilter.MDC_USER_ID, user.userId().toString());
        }
        chain.doFilter(request, response);
    }
}
