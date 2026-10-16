package com.helpdesk.user;

import com.helpdesk.security.Role;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Composable user filters. Every query starts with {@link #inOrganization(UUID)}. */
final class UserSpecifications {

    private UserSpecifications() {
    }

    static Specification<User> inOrganization(UUID organizationId) {
        return (root, query, cb) -> cb.equal(root.get("organizationId"), organizationId);
    }

    static Specification<User> matching(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(q.strip().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("fullName")), pattern, '\\'),
                cb.like(root.get("email"), pattern, '\\'));
    }

    static Specification<User> hasRole(Role role) {
        return role == null ? null : (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    static Specification<User> hasAnyRole(Collection<Role> roles) {
        return (root, query, cb) -> root.get("role").in(roles);
    }

    static Specification<User> isActive(Boolean active) {
        return active == null ? null : (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
