package com.helpdesk.organization;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Organizations are the tenant root, not tenant-owned, so a lookup by primary key is correct here:
 * the id always comes from the authenticated actor.
 */
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
