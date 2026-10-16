package com.helpdesk.security;

import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Turns a validated JWT into an {@link AuthenticatedUserToken} on every request (05 §2.1):
 * <ol>
 *   <li>reads {@code sub} (user id) and the {@code org} claim;</li>
 *   <li>loads the user scoped to that org and requires it to be active — otherwise {@code 401} (FR-4);</li>
 *   <li>builds the principal from the database values, so role changes apply immediately.</li>
 * </ol>
 */
@Component
public class AuthenticatedUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final PrincipalLoader principalLoader;

    public AuthenticatedUserConverter(PrincipalLoader principalLoader) {
        this.principalLoader = principalLoader;
    }

    @Override
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        UUID userId = parse(jwt.getSubject());
        UUID orgId = parse(jwt.getClaimAsString(JwtTokenService.CLAIM_ORG));
        AuthenticatedUser principal = principalLoader.loadActive(userId, orgId)
                .orElseThrow(() -> new InvalidBearerTokenException("User is unknown or deactivated"));
        return new AuthenticatedUserToken(principal, jwt);
    }

    private static UUID parse(String value) {
        if (value == null) {
            throw new InvalidBearerTokenException("Token is missing required claims");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidBearerTokenException("Token has malformed claims");
        }
    }
}
