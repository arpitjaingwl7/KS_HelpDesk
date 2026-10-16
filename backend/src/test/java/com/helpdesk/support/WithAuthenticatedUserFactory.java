package com.helpdesk.support;

import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.security.AuthenticatedUserToken;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

public class WithAuthenticatedUserFactory implements WithSecurityContextFactory<WithAuthenticatedUser> {

    @Override
    public SecurityContext createSecurityContext(WithAuthenticatedUser annotation) {
        AuthenticatedUser user = new AuthenticatedUser(UUID.fromString(annotation.userId()),
                UUID.fromString(annotation.organizationId()), annotation.role(), "test@example.test");
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "HS256").subject(annotation.userId())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new AuthenticatedUserToken(user, jwt));
        return context;
    }
}
