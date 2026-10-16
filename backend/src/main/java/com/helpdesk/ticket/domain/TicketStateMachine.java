package com.helpdesk.ticket.domain;

import static com.helpdesk.ticket.domain.TicketStatus.CLOSED;
import static com.helpdesk.ticket.domain.TicketStatus.IN_PROGRESS;
import static com.helpdesk.ticket.domain.TicketStatus.ON_HOLD;
import static com.helpdesk.ticket.domain.TicketStatus.OPEN;
import static com.helpdesk.ticket.domain.TicketStatus.RESOLVED;

import com.helpdesk.common.error.ApiException;
import com.helpdesk.common.error.ErrorCode;
import com.helpdesk.security.Role;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The ticket status state machine from docs/03 §3 — pure Java, no Spring, exhaustively unit-tested.
 *
 * <pre>
 * From \ To     OPEN   IN_PROGRESS  ON_HOLD  RESOLVED  CLOSED
 * OPEN           —        A           A        A        A,C
 * IN_PROGRESS    A        —           A        A        A,C
 * ON_HOLD        —        A           —        A        A,C
 * RESOLVED      A,C       —           —        —        A,C
 * CLOSED         A        —           —        —         —
 * </pre>
 * A = AGENT or ORG_ADMIN, C = the customer who requested the ticket.
 */
public final class TicketStateMachine {

    private static final Map<TicketStatus, Set<TicketStatus>> STAFF = new EnumMap<>(TicketStatus.class);
    private static final Map<TicketStatus, Set<TicketStatus>> CUSTOMER = new EnumMap<>(TicketStatus.class);

    static {
        STAFF.put(OPEN, EnumSet.of(IN_PROGRESS, ON_HOLD, RESOLVED, CLOSED));
        STAFF.put(IN_PROGRESS, EnumSet.of(OPEN, ON_HOLD, RESOLVED, CLOSED));
        STAFF.put(ON_HOLD, EnumSet.of(IN_PROGRESS, RESOLVED, CLOSED));
        STAFF.put(RESOLVED, EnumSet.of(OPEN, CLOSED));
        STAFF.put(CLOSED, EnumSet.of(OPEN));

        // BR-4: customers may close from any non-closed status, and reopen only a RESOLVED ticket.
        CUSTOMER.put(OPEN, EnumSet.of(CLOSED));
        CUSTOMER.put(IN_PROGRESS, EnumSet.of(CLOSED));
        CUSTOMER.put(ON_HOLD, EnumSet.of(CLOSED));
        CUSTOMER.put(RESOLVED, EnumSet.of(OPEN, CLOSED));
        CUSTOMER.put(CLOSED, EnumSet.noneOf(TicketStatus.class));
    }

    private TicketStateMachine() {
    }

    /** Whether the transition exists for anyone (i.e. for staff). */
    public static boolean exists(TicketStatus from, TicketStatus to) {
        return STAFF.get(from).contains(to);
    }

    public static boolean isAllowed(Role role, TicketStatus from, TicketStatus to) {
        return table(role).get(from).contains(to);
    }

    /** The target statuses this role may move a ticket to, in declaration order (for the UI). */
    public static List<TicketStatus> allowedTargets(Role role, TicketStatus from) {
        Set<TicketStatus> targets = table(role).get(from);
        return targets.isEmpty() ? Collections.emptyList() : List.copyOf(EnumSet.copyOf(targets));
    }

    /**
     * @throws ApiException {@code 409 INVALID_STATUS_TRANSITION} if the transition doesn't exist
     *                      (including "to the same status"), or {@code 403 ACCESS_DENIED} if it
     *                      exists but this role may not make it
     */
    public static void validate(Role role, TicketStatus from, TicketStatus to) {
        if (!exists(from, to)) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot change status from " + from + " to " + to);
        }
        if (!isAllowed(role, from, to)) {
            throw ApiException.accessDenied("Your role cannot change status from " + from + " to " + to);
        }
    }

    private static Map<TicketStatus, Set<TicketStatus>> table(Role role) {
        return role == Role.CUSTOMER ? CUSTOMER : STAFF;
    }
}
