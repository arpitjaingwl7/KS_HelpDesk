package com.helpdesk.ticket.repository;

import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.ticket.domain.Ticket;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;

/**
 * Builds the ticket search query. It <strong>always</strong> starts with
 * {@code organization_id = actor.org}, and for customers it <strong>always</strong> adds
 * {@code requester_id = actor.user} regardless of the filters they send (FR-19).
 */
public final class TicketSpecifications {

    private TicketSpecifications() {
    }

    public static Specification<Ticket> search(AuthenticatedUser actor, TicketSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("organizationId"), actor.organizationId()));

            if (actor.isCustomer()) {
                where.add(cb.equal(root.get("requester").get("id"), actor.userId()));
            } else {
                if (c.requesterId() != null) {
                    where.add(cb.equal(root.get("requester").get("id"), c.requesterId()));
                }
                if (c.unassigned()) {
                    where.add(cb.isNull(root.get("assignee")));
                } else if (c.assigneeId() != null) {
                    where.add(cb.equal(root.get("assignee").get("id"), c.assigneeId()));
                }
            }

            if (!c.statuses().isEmpty()) {
                where.add(root.get("status").in(c.statuses()));
            }
            if (!c.priorities().isEmpty()) {
                where.add(root.get("priority").in(c.priorities()));
            }
            if (c.createdFrom() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("createdAt"), c.createdFrom()));
            }
            if (c.createdTo() != null) {
                where.add(cb.lessThan(root.get("createdAt"), c.createdTo()));
            }
            if (c.q() != null && !c.q().isBlank()) {
                where.add(textMatch((HibernateCriteriaBuilder) cb, root, c.q().strip()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    /** Case-insensitive ILIKE on subject and description (trigram-indexed), plus the number if numeric. */
    private static Predicate textMatch(HibernateCriteriaBuilder cb, jakarta.persistence.criteria.Root<Ticket> root, String q) {
        String term = q.startsWith("#") ? q.substring(1) : q;
        String pattern = "%" + escapeLike(q) + "%";
        List<Predicate> any = new ArrayList<>();
        any.add(cb.ilike(root.get("subject"), pattern, '\\'));
        any.add(cb.ilike(root.get("description"), pattern, '\\'));
        if (term.matches("\\d{1,18}")) {
            any.add(cb.equal(root.get("number"), Long.parseLong(term)));
        }
        return cb.or(any.toArray(Predicate[]::new));
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Convenience for callers that only need the org predicate (e.g. counting). */
    public static Specification<Ticket> inOrganization(UUID organizationId) {
        return (root, query, cb) -> cb.equal(root.get("organizationId"), organizationId);
    }
}
