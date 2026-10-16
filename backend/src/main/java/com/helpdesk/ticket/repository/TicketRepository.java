package com.helpdesk.ticket.repository;

import com.helpdesk.ticket.domain.Ticket;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Tenant-owned: only org-scoped methods may be used. The inherited {@code findById}/{@code findAll()}
 * family is forbidden by {@code ArchitectureTest}; {@link #findAll(Specification, Pageable)} is allowed
 * because {@link TicketSpecifications#search} always starts with the organization predicate.
 */
public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {

    @EntityGraph(attributePaths = {"requester", "assignee"})
    Optional<Ticket> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** Requester and assignee are fetched in the same query — no N+1 on the list page. */
    @Override
    @EntityGraph(attributePaths = {"requester", "assignee"})
    Page<Ticket> findAll(Specification<Ticket> spec, Pageable pageable);

    /**
     * Atomically allocates the next per-org ticket number (BR-2). {@code UPDATE … RETURNING} yields a
     * result set, so it runs as a query (no {@code @Modifying}). The row lock serializes concurrent
     * creates within one org only. Must be called inside the (non-read-only) create transaction.
     */
    @Query(value = "UPDATE organizations SET ticket_sequence = ticket_sequence + 1 WHERE id = :orgId RETURNING ticket_sequence",
            nativeQuery = true)
    long nextTicketNumber(@Param("orgId") UUID orgId);

    @Query("select t.status, count(t) from Ticket t where t.organizationId = :orgId group by t.status")
    List<Object[]> countByStatus(@Param("orgId") UUID orgId);

    @Query("select t.priority, count(t) from Ticket t where t.organizationId = :orgId and t.status <> com.helpdesk.ticket.domain.TicketStatus.CLOSED group by t.priority")
    List<Object[]> countOpenByPriority(@Param("orgId") UUID orgId);

    @Query("select count(t) from Ticket t where t.organizationId = :orgId and t.assignee is null and t.status <> com.helpdesk.ticket.domain.TicketStatus.CLOSED")
    long countUnassignedNotClosed(@Param("orgId") UUID orgId);
}
