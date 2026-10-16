package com.helpdesk.user;

import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.security.PrincipalLoader;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the caller on every request: the user must exist <em>in the org named by the token</em>
 * and be active. The principal is built from database values (role included).
 */
@Component
class UserPrincipalLoader implements PrincipalLoader {

    private final UserRepository users;

    UserPrincipalLoader(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> loadActive(UUID userId, UUID organizationId) {
        return users.findByIdAndOrganizationId(userId, organizationId)
                .filter(User::isActive)
                .map(u -> new AuthenticatedUser(u.getId(), u.getOrganizationId(), u.getRole(), u.getEmail()));
    }
}
