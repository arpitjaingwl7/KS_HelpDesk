package com.helpdesk.history;

import com.helpdesk.history.dto.HistoryEntryResponse;
import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.ticket.domain.Ticket;
import com.helpdesk.ticket.domain.TicketAccessPolicy;
import com.helpdesk.user.UserService;
import com.helpdesk.user.dto.UserRef;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads a ticket's history (FR-23): oldest first, at most 500 entries, user names resolved in one batch. */
@Service
public class HistoryService {

    static final int MAX_ENTRIES = 500;

    private final TicketHistoryRepository history;
    private final TicketAccessPolicy policy;
    private final UserService userService;

    public HistoryService(TicketHistoryRepository history, TicketAccessPolicy policy, UserService userService) {
        this.history = history;
        this.policy = policy;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<HistoryEntryResponse> list(AuthenticatedUser actor, UUID ticketId) {
        Ticket ticket = policy.loadVisible(actor, ticketId);
        List<TicketHistory> entries = history.findByOrganizationIdAndTicketId(actor.organizationId(), ticket.getId(),
                PageRequest.of(0, MAX_ENTRIES, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))));

        Set<UUID> userIds = new HashSet<>();
        for (TicketHistory e : entries) {
            userIds.add(e.getActorId());
            if (e.getEventType() == HistoryEventType.ASSIGNEE_CHANGED) {
                addIfUuid(userIds, e.getOldValue());
                addIfUuid(userIds, e.getNewValue());
            }
        }
        Map<UUID, UserRef> users = userService.refsById(actor.organizationId(), userIds);

        return entries.stream().map(e -> new HistoryEntryResponse(
                e.getId(),
                e.getEventType(),
                value(e, e.getOldValue(), users),
                value(e, e.getNewValue(), users),
                users.getOrDefault(e.getActorId(), new UserRef(e.getActorId(), "Unknown user")),
                e.getCreatedAt())).toList();
    }

    private static Object value(TicketHistory e, String raw, Map<UUID, UserRef> users) {
        if (raw == null || e.getEventType() != HistoryEventType.ASSIGNEE_CHANGED) {
            return raw;
        }
        UUID id = UUID.fromString(raw);
        return users.getOrDefault(id, new UserRef(id, "Unknown user"));
    }

    private static void addIfUuid(Set<UUID> ids, String raw) {
        if (raw != null) {
            ids.add(UUID.fromString(raw));
        }
    }
}
