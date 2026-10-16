package com.helpdesk.history;

import com.helpdesk.common.persistence.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Append-only audit entry (FR-22). For {@code ASSIGNEE_CHANGED} the values are user ids, resolved
 * to display names at read time in one batched lookup; otherwise they are enum names.
 */
@Entity
@Immutable
@Table(name = "ticket_history")
public class TicketHistory extends TenantOwnedEntity {

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private UUID ticketId;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 20)
    private HistoryEventType eventType;

    @Column(name = "old_value", updatable = false, length = 100)
    private String oldValue;

    @Column(name = "new_value", updatable = false, length = 100)
    private String newValue;

    protected TicketHistory() {
    }

    public TicketHistory(UUID organizationId, UUID ticketId, UUID actorId, HistoryEventType eventType,
                         String oldValue, String newValue) {
        super(organizationId);
        this.ticketId = ticketId;
        this.actorId = actorId;
        this.eventType = eventType;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public HistoryEventType getEventType() {
        return eventType;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }
}
