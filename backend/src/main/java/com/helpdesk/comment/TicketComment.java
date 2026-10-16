package com.helpdesk.comment;

import com.helpdesk.common.persistence.TenantOwnedEntity;
import com.helpdesk.ticket.domain.CommentVisibility;
import com.helpdesk.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** A PUBLIC reply or an INTERNAL note. Comments cannot be edited or deleted in v1. */
@Entity
@Immutable
@Table(name = "ticket_comments")
public class TicketComment extends TenantOwnedEntity {

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private UUID ticketId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private CommentVisibility visibility;

    protected TicketComment() {
    }

    public TicketComment(UUID organizationId, UUID ticketId, User author, String body, CommentVisibility visibility) {
        super(organizationId);
        if (!author.belongsTo(organizationId)) {
            throw new IllegalArgumentException("Author belongs to a different organization");
        }
        this.ticketId = ticketId;
        this.author = author;
        this.body = body;
        this.visibility = visibility;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public User getAuthor() {
        return author;
    }

    public String getBody() {
        return body;
    }

    public CommentVisibility getVisibility() {
        return visibility;
    }
}
