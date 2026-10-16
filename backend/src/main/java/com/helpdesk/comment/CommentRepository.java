package com.helpdesk.comment;

import com.helpdesk.ticket.domain.CommentVisibility;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Tenant-owned: every method takes {@code organizationId}. Customers are served by the
 * visibility-filtered query, so INTERNAL notes are excluded <em>in SQL</em> and page counts stay correct.
 */
public interface CommentRepository extends JpaRepository<TicketComment, UUID> {

    @EntityGraph(attributePaths = "author")
    Page<TicketComment> findByOrganizationIdAndTicketId(UUID organizationId, UUID ticketId, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Page<TicketComment> findByOrganizationIdAndTicketIdAndVisibility(UUID organizationId, UUID ticketId,
                                                                     CommentVisibility visibility, Pageable pageable);
}
