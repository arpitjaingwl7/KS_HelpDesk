package com.helpdesk.auth;

import com.helpdesk.auth.dto.AuthResponse;
import com.helpdesk.auth.dto.CurrentUserResponse;
import com.helpdesk.auth.dto.LoginRequest;
import com.helpdesk.auth.dto.RegisterOrganizationRequest;
import com.helpdesk.common.error.ApiException;
import com.helpdesk.common.error.ErrorCode;
import com.helpdesk.organization.Organization;
import com.helpdesk.organization.OrganizationService;
import com.helpdesk.organization.dto.OrganizationRef;
import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.security.JwtTokenService;
import com.helpdesk.user.User;
import com.helpdesk.user.UserService;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and login (FR-1, FR-5, FR-6). Every login failure — unknown org, unknown email,
 * wrong password, inactive user — returns the same {@code 401 INVALID_CREDENTIALS}, and a dummy
 * BCrypt check runs when no user was found, so response timing doesn't reveal what exists (T7).
 */
@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Organization, email or password is incorrect";

    private final OrganizationService organizationService;
    private final UserService userService;
    private final JwtTokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    public AuthService(OrganizationService organizationService, UserService userService,
                       JwtTokenService tokenService, PasswordEncoder passwordEncoder) {
        this.organizationService = organizationService;
        this.userService = userService;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-constant-time-checks");
    }

    /** Creates the org and its first ORG_ADMIN in one transaction, then logs the admin in. */
    @Transactional
    public AuthResponse registerOrganization(RegisterOrganizationRequest request) {
        Organization org = organizationService.register(request.organizationName(), request.organizationSlug());
        User admin = userService.createInitialAdmin(org.getId(), request.adminFullName(), request.adminEmail(),
                request.adminPassword());
        return issue(admin, new OrganizationRef(org.getId(), org.getName(), org.getSlug()));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Optional<Organization> org = organizationService.findBySlug(request.organizationSlug());
        Optional<User> user = org.flatMap(o -> userService.findByEmail(o.getId(), request.email()));

        // Always run exactly one BCrypt comparison.
        boolean passwordMatches = passwordEncoder.matches(request.password(),
                user.map(User::getPasswordHash).orElse(dummyHash));
        if (user.isEmpty() || !passwordMatches || !user.get().isActive()) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, INVALID_CREDENTIALS);
        }

        User authenticated = user.get();
        userService.recordLogin(authenticated);
        Organization o = org.get();
        return issue(authenticated, new OrganizationRef(o.getId(), o.getName(), o.getSlug()));
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse me(AuthenticatedUser actor) {
        User user = userService.requireActor(actor);
        return toCurrentUser(user, organizationService.getRef(actor.organizationId()));
    }

    private AuthResponse issue(User user, OrganizationRef org) {
        JwtTokenService.IssuedToken token = tokenService.issue(user.getId(), user.getOrganizationId(), user.getRole());
        return AuthResponse.bearer(token.value(), token.expiresInSeconds(), toCurrentUser(user, org));
    }

    private static CurrentUserResponse toCurrentUser(User user, OrganizationRef org) {
        return new CurrentUserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), org);
    }
}
