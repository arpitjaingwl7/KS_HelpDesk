package com.helpdesk.user;

import static com.helpdesk.user.UserSpecifications.hasAnyRole;
import static com.helpdesk.user.UserSpecifications.hasRole;
import static com.helpdesk.user.UserSpecifications.inOrganization;
import static com.helpdesk.user.UserSpecifications.isActive;
import static com.helpdesk.user.UserSpecifications.matching;

import com.helpdesk.common.error.ApiException;
import com.helpdesk.common.error.ErrorCode;
import com.helpdesk.common.web.PageResponse;
import com.helpdesk.common.web.SortSanitizer;
import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.security.Role;
import com.helpdesk.user.dto.CreateUserRequest;
import com.helpdesk.user.dto.UpdateUserRequest;
import com.helpdesk.user.dto.UserDetail;
import com.helpdesk.user.dto.UserOption;
import com.helpdesk.user.dto.UserRef;
import com.helpdesk.user.dto.UserSummary;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User management inside the actor's organization (FR-3, BR-8, BR-9), plus the user lookups
 * other features need (assignable agents, active customers, display names).
 */
@Service
public class UserService {

    static final int PICKER_LIMIT = 50;
    static final Map<String, String> SORTABLE = Map.of(
            "fullName", "fullName", "email", "email", "role", "role", "createdAt", "createdAt", "active", "active");
    static final Sort DEFAULT_SORT = Sort.by("fullName").ascending();

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper mapper;
    private final Clock clock;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, UserMapper mapper, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
        this.clock = clock;
    }

    // ───────────── admin use cases ─────────────

    @Transactional
    public UserDetail create(AuthenticatedUser actor, CreateUserRequest request) {
        requireAdmin(actor);
        User user = newUser(actor.organizationId(), request.email(), request.password(), request.fullName(), request.role());
        return mapper.toDetail(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummary> list(AuthenticatedUser actor, String q, Role role, Boolean active, Pageable pageable) {
        requireAdmin(actor);
        Specification<User> spec = Specification.allOf(inOrganization(actor.organizationId()),
                matching(q), hasRole(role), isActive(active));
        Pageable page = SortSanitizer.sanitize(pageable, SORTABLE, DEFAULT_SORT);
        return PageResponse.of(users.findAll(spec, page), mapper::toSummary);
    }

    @Transactional(readOnly = true)
    public UserDetail get(AuthenticatedUser actor, UUID userId) {
        requireAdmin(actor);
        return mapper.toDetail(load(actor.organizationId(), userId));
    }

    @Transactional
    public UserDetail update(AuthenticatedUser actor, UUID userId, UpdateUserRequest request) {
        requireAdmin(actor);
        User user = load(actor.organizationId(), userId);
        if (!Objects.equals(user.getVersion(), request.version())) {
            throw ApiException.concurrentModification("User");
        }
        boolean deactivating = Boolean.FALSE.equals(request.active()) && user.isActive();
        boolean demoting = request.role() != null && request.role() != Role.ORG_ADMIN && user.getRole() == Role.ORG_ADMIN;

        if (deactivating && user.getId().equals(actor.userId())) {
            throw new ApiException(ErrorCode.SELF_DEACTIVATION, "You cannot deactivate your own account");
        }
        if ((deactivating || demoting) && user.isActiveAdmin()
                && users.countByOrganizationIdAndRoleAndActiveTrue(actor.organizationId(), Role.ORG_ADMIN) <= 1) {
            throw new ApiException(ErrorCode.LAST_ADMIN, "The organization must keep at least one active admin");
        }

        if (request.fullName() != null) {
            user.rename(request.fullName());
        }
        if (request.role() != null) {
            user.changeRole(request.role());
        }
        if (request.active() != null) {
            user.setActive(request.active());
        }
        users.flush();
        return mapper.toDetail(user);
    }

    // ───────────── pickers (ORG_ADMIN, AGENT) ─────────────

    @Transactional(readOnly = true)
    public List<UserOption> assignableAgents(AuthenticatedUser actor, String q) {
        return picker(actor, EnumSet.of(Role.AGENT, Role.ORG_ADMIN), q);
    }

    @Transactional(readOnly = true)
    public List<UserOption> activeCustomers(AuthenticatedUser actor, String q) {
        return picker(actor, EnumSet.of(Role.CUSTOMER), q);
    }

    private List<UserOption> picker(AuthenticatedUser actor, Set<Role> roles, String q) {
        if (!actor.isStaff()) {
            throw ApiException.accessDenied("Only staff can list agents and customers");
        }
        Specification<User> spec = Specification.allOf(inOrganization(actor.organizationId()),
                hasAnyRole(roles), isActive(true), matching(q));
        return users.findAll(spec, PageRequest.of(0, PICKER_LIMIT, DEFAULT_SORT)).map(mapper::toOption).getContent();
    }

    // ───────────── used by auth / registration ─────────────

    /** Creates a user without an actor — only for organization registration and demo seeding. */
    @Transactional
    public User createInitialAdmin(UUID organizationId, String fullName, String email, String rawPassword) {
        return newUser(organizationId, email, rawPassword, fullName, Role.ORG_ADMIN);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(UUID organizationId, String email) {
        return users.findByOrganizationIdAndEmail(organizationId, User.normalizeEmail(email));
    }

    @Transactional
    public void recordLogin(User user) {
        users.findByIdAndOrganizationId(user.getId(), user.getOrganizationId())
                .ifPresent(u -> u.recordLogin(Instant.now(clock)));
    }

    // ───────────── used by other features ─────────────

    /** The actor's own user row. Missing means the token outlived the user → treated as not found. */
    @Transactional(readOnly = true)
    public User requireActor(AuthenticatedUser actor) {
        return load(actor.organizationId(), actor.userId());
    }

    /** An active AGENT or ORG_ADMIN in this org; empty for any other user, including other orgs' users. */
    @Transactional(readOnly = true)
    public Optional<User> findAssignable(UUID organizationId, UUID userId) {
        return users.findByIdAndOrganizationId(userId, organizationId).filter(User::isAssignable);
    }

    /** An active CUSTOMER in this org; empty for any other user. */
    @Transactional(readOnly = true)
    public Optional<User> findActiveCustomer(UUID organizationId, UUID userId) {
        return users.findByIdAndOrganizationId(userId, organizationId).filter(User::isActiveCustomer);
    }

    /** Batched id → display name lookup, always scoped to the organization. */
    @Transactional(readOnly = true)
    public Map<UUID, UserRef> refsById(UUID organizationId, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findByOrganizationIdAndIdIn(organizationId, ids).stream()
                .map(mapper::toRef)
                .collect(Collectors.toMap(UserRef::id, Function.identity()));
    }

    // ───────────── internals ─────────────

    private User newUser(UUID organizationId, String email, String rawPassword, String fullName, Role role) {
        String normalized = User.normalizeEmail(email);
        if (users.existsByOrganizationIdAndEmail(organizationId, normalized)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS, "A user with email " + normalized + " already exists");
        }
        User user = new User(organizationId, normalized, passwordEncoder.encode(rawPassword), fullName, role);
        return users.saveAndFlush(user);
    }

    private User load(UUID organizationId, UUID userId) {
        return users.findByIdAndOrganizationId(userId, organizationId)
                .orElseThrow(() -> ApiException.notFound("User", userId));
    }

    private static void requireAdmin(AuthenticatedUser actor) {
        if (!actor.isAdmin()) {
            throw ApiException.accessDenied("Only an organization admin can manage users");
        }
    }
}
