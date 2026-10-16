package com.helpdesk.organization;

import com.helpdesk.common.error.ApiException;
import com.helpdesk.common.error.ErrorCode;
import com.helpdesk.organization.dto.OrganizationRef;
import com.helpdesk.organization.dto.OrganizationResponse;
import com.helpdesk.security.AuthenticatedUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private final OrganizationRepository organizations;

    public OrganizationService(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    /** Creates a tenant. Must run inside the caller's registration transaction. */
    @Transactional
    public Organization register(String name, String slug) {
        if (organizations.existsBySlug(slug)) {
            throw new ApiException(ErrorCode.SLUG_ALREADY_EXISTS, "Organization slug '" + slug + "' is already taken");
        }
        return organizations.saveAndFlush(new Organization(name, slug));
    }

    @Transactional(readOnly = true)
    public Optional<Organization> findBySlug(String slug) {
        return organizations.findBySlug(slug);
    }

    @Transactional(readOnly = true)
    public OrganizationResponse get(AuthenticatedUser actor) {
        return toResponse(load(actor.organizationId()));
    }

    @Transactional(readOnly = true)
    public OrganizationRef getRef(UUID organizationId) {
        Organization org = load(organizationId);
        return new OrganizationRef(org.getId(), org.getName(), org.getSlug());
    }

    @Transactional
    public OrganizationResponse rename(AuthenticatedUser actor, String newName) {
        if (!actor.isAdmin()) {
            throw ApiException.accessDenied("Only an organization admin can rename the organization");
        }
        Organization org = load(actor.organizationId());
        org.rename(newName);
        organizations.flush();
        return toResponse(org);
    }

    private Organization load(UUID organizationId) {
        return organizations.findById(organizationId)
                .orElseThrow(() -> ApiException.notFound("Organization", organizationId));
    }

    private static OrganizationResponse toResponse(Organization org) {
        return new OrganizationResponse(org.getId(), org.getName(), org.getSlug(), org.getCreatedAt());
    }
}
