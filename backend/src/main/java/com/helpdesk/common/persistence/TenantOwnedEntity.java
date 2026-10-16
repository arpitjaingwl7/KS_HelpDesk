package com.helpdesk.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.Objects;
import java.util.UUID;

/**
 * Base class for every row that belongs to a tenant. {@code organizationId} is set exactly once,
 * from the authenticated actor — never from a request DTO (BR-1) — and can never be updated.
 */
@MappedSuperclass
public abstract class TenantOwnedEntity extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    protected TenantOwnedEntity() {
    }

    protected TenantOwnedEntity(UUID organizationId) {
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public boolean belongsTo(UUID orgId) {
        return organizationId.equals(orgId);
    }
}
