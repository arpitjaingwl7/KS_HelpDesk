package com.helpdesk.user;

import com.helpdesk.security.Role;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Tenant-owned: every query method takes {@code organizationId}. The inherited
 * {@code findById}/{@code findAll}/{@code getReferenceById}/{@code existsById} must not be used —
 * enforced by {@code ArchitectureTest}.
 */
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<User> findByOrganizationIdAndEmail(UUID organizationId, String email);

    boolean existsByOrganizationIdAndEmail(UUID organizationId, String email);

    long countByOrganizationIdAndRoleAndActiveTrue(UUID organizationId, Role role);

    List<User> findByOrganizationIdAndIdIn(UUID organizationId, Collection<UUID> ids);
}
