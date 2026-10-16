package com.helpdesk.support;

import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.security.Role;
import com.helpdesk.ticket.domain.Ticket;
import com.helpdesk.ticket.domain.TicketPriority;
import com.helpdesk.ticket.domain.TicketStatus;
import com.helpdesk.user.User;
import java.time.Instant;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

/** Test data builders for unit tests. Entities get a version as if they had been loaded from the database. */
public final class Fixtures {

    public static final Instant NOW = Instant.parse("2026-10-15T09:30:00Z");

    private Fixtures() {
    }

    public static User user(UUID orgId, Role role) {
        return user(orgId, role, role.name().toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
    }

    public static User user(UUID orgId, Role role, String email) {
        User user = new User(orgId, email, "{bcrypt}hash", role.name() + " Person", role);
        ReflectionTestUtils.setField(user, "version", 0L);
        ReflectionTestUtils.setField(user, "createdAt", NOW);
        return user;
    }

    public static User inactive(User user) {
        user.setActive(false);
        return user;
    }

    public static AuthenticatedUser actor(User user) {
        return new AuthenticatedUser(user.getId(), user.getOrganizationId(), user.getRole(), user.getEmail());
    }

    public static Ticket ticket(User requester) {
        return ticket(requester, TicketStatus.OPEN);
    }

    public static Ticket ticket(User requester, TicketStatus status) {
        Ticket ticket = Ticket.open(requester.getOrganizationId(), 42L, "Cannot reset password",
                "The reset email never arrives", TicketPriority.MEDIUM, requester);
        ReflectionTestUtils.setField(ticket, "status", status);
        ReflectionTestUtils.setField(ticket, "version", 3L);
        ReflectionTestUtils.setField(ticket, "createdAt", NOW);
        ReflectionTestUtils.setField(ticket, "updatedAt", NOW);
        return ticket;
    }
}
