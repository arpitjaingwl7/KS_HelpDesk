package com.helpdesk.security;

import java.util.Optional;
import java.util.UUID;

/**
 * Port used by {@link AuthenticatedUserConverter} to re-load the caller on every request.
 * Implemented by the {@code user} feature, so {@code security} never depends on it.
 */
public interface PrincipalLoader {

    /**
     * @return the principal if a user with this id exists <em>in this organization</em> and is active
     */
    Optional<AuthenticatedUser> loadActive(UUID userId, UUID organizationId);
}
