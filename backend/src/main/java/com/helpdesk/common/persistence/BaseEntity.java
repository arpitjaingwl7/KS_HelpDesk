package com.helpdesk.common.persistence;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Root of every entity: a UUIDv7 id (time-ordered, index friendly, unguessable — ADR-006)
 * assigned in Java, plus an audited {@code createdAt}.
 *
 * <p>Implements {@link Persistable} so that {@code save()} on a new entity issues a plain
 * {@code INSERT} instead of a {@code SELECT} + merge (the id is assigned before persisting).
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity implements Persistable<UUID> {

    @Id
    private UUID id = UuidCreator.getTimeOrderedEpoch();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    @Override
    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof BaseEntity other && getClass() == other.getClass() && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
