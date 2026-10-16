package com.helpdesk.history;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-owned: every method takes {@code organizationId}. */
public interface TicketHistoryRepository extends JpaRepository<TicketHistory, UUID> {

    List<TicketHistory> findByOrganizationIdAndTicketId(UUID organizationId, UUID ticketId, Pageable pageable);
}
