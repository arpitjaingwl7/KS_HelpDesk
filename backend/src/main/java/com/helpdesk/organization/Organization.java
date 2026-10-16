package com.helpdesk.organization;

import com.helpdesk.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import org.springframework.data.annotation.LastModifiedDate;

/** A tenant. The slug is used at login and can never change (FR-8). */
@Entity
@Table(name = "organizations")
public class Organization extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 50, updatable = false)
    private String slug;

    /**
     * Per-org ticket counter. Only ever advanced by the atomic {@code UPDATE … RETURNING} in
     * {@code TicketRepository.nextTicketNumber}; never written by Hibernate after insert, so a
     * dirty Organization can't overwrite a newer counter value.
     */
    @Column(name = "ticket_sequence", nullable = false, updatable = false)
    private long ticketSequence;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Organization() {
    }

    public Organization(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    public void rename(String newName) {
        this.name = newName;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
