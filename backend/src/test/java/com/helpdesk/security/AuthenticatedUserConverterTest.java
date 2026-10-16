package com.helpdesk.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

@ExtendWith(MockitoExtension.class)
class AuthenticatedUserConverterTest {

    @Mock
    PrincipalLoader loader;

    final UUID userId = UUID.randomUUID();
    final UUID orgId = UUID.randomUUID();

    private Jwt jwt(String sub, String org, String role) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "HS256").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (sub != null) {
            b.subject(sub);
        }
        if (org != null) {
            b.claim("org", org);
        }
        return b.claim("role", role).build();
    }

    @Test
    void activeUserWithMatchingOrg_principalBuiltFromDatabaseValues() {
        // The token still says CUSTOMER, but the database says AGENT — the database wins.
        when(loader.loadActive(userId, orgId)).thenReturn(Optional.of(new AuthenticatedUser(userId, orgId, Role.AGENT, "a@b.c")));

        AuthenticatedUserToken token = (AuthenticatedUserToken) new AuthenticatedUserConverter(loader)
                .convert(jwt(userId.toString(), orgId.toString(), "CUSTOMER"));

        assertThat(token.getPrincipal().role()).isEqualTo(Role.AGENT);
        assertThat(token.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_AGENT");
        assertThat(token.isAuthenticated()).isTrue();
        assertThat(token.getName()).isEqualTo(userId.toString());
    }

    @Test
    void deactivatedUser_or_orgMismatch_isRejected() {
        when(loader.loadActive(userId, orgId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new AuthenticatedUserConverter(loader).convert(jwt(userId.toString(), orgId.toString(), "AGENT")))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void missingOrMalformedClaims_areRejected() {
        AuthenticatedUserConverter converter = new AuthenticatedUserConverter(loader);
        assertThatThrownBy(() -> converter.convert(jwt(userId.toString(), null, "AGENT")))
                .isInstanceOf(InvalidBearerTokenException.class);
        assertThatThrownBy(() -> converter.convert(jwt("not-a-uuid", orgId.toString(), "AGENT")))
                .isInstanceOf(InvalidBearerTokenException.class);
    }
}
