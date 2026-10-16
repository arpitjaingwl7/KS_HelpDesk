package com.helpdesk.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.helpdesk.common.error.ApiException;
import com.helpdesk.common.error.ErrorCode;
import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.security.Role;
import com.helpdesk.support.Fixtures;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.user.User;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Role × action × ownership × status, mirroring the matrix in docs/04 §4. */
@ExtendWith(MockitoExtension.class)
class TicketAccessPolicyTest {

    @Mock
    TicketRepository tickets;

    TicketAccessPolicy policy;

    final UUID orgId = UUID.randomUUID();
    final User admin = Fixtures.user(orgId, Role.ORG_ADMIN);
    final User agent = Fixtures.user(orgId, Role.AGENT);
    final User customer = Fixtures.user(orgId, Role.CUSTOMER);
    final User otherCustomer = Fixtures.user(orgId, Role.CUSTOMER);

    @BeforeEach
    void setUp() {
        policy = new TicketAccessPolicy(tickets);
    }

    // ───────────── visibility ─────────────

    @Test
    void loadVisible_ticketOfOtherOrg_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(tickets.findByIdAndOrganizationId(id, orgId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> policy.loadVisible(Fixtures.actor(agent), id))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void loadVisible_otherCustomersTicket_throwsNotFound_not403() {
        Ticket t = Fixtures.ticket(otherCustomer);
        when(tickets.findByIdAndOrganizationId(t.getId(), orgId)).thenReturn(Optional.of(t));
        assertThatThrownBy(() -> policy.loadVisible(Fixtures.actor(customer), t.getId()))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void loadVisible_ownTicketForCustomer_andAnyTicketForStaff() {
        Ticket t = Fixtures.ticket(customer);
        when(tickets.findByIdAndOrganizationId(t.getId(), orgId)).thenReturn(Optional.of(t));
        assertThat(policy.loadVisible(Fixtures.actor(customer), t.getId())).isSameAs(t);
        assertThat(policy.loadVisible(Fixtures.actor(agent), t.getId())).isSameAs(t);
        assertThat(policy.loadVisible(Fixtures.actor(admin), t.getId())).isSameAs(t);
    }

    // ───────────── edit (BR-3, BR-6) ─────────────

    static Stream<Arguments> editCases() {
        return Stream.of(
                // role, status, changesPriority, expected (null = allowed)
                Arguments.of(Role.CUSTOMER, TicketStatus.OPEN, false, null),
                Arguments.of(Role.CUSTOMER, TicketStatus.OPEN, true, ErrorCode.ACCESS_DENIED),
                Arguments.of(Role.CUSTOMER, TicketStatus.IN_PROGRESS, false, ErrorCode.ACCESS_DENIED),
                Arguments.of(Role.CUSTOMER, TicketStatus.RESOLVED, false, ErrorCode.ACCESS_DENIED),
                Arguments.of(Role.CUSTOMER, TicketStatus.CLOSED, false, ErrorCode.TICKET_CLOSED),
                Arguments.of(Role.AGENT, TicketStatus.IN_PROGRESS, true, null),
                Arguments.of(Role.AGENT, TicketStatus.RESOLVED, true, null),
                Arguments.of(Role.AGENT, TicketStatus.CLOSED, false, ErrorCode.TICKET_CLOSED),
                Arguments.of(Role.ORG_ADMIN, TicketStatus.ON_HOLD, true, null),
                Arguments.of(Role.ORG_ADMIN, TicketStatus.CLOSED, true, ErrorCode.TICKET_CLOSED));
    }

    @ParameterizedTest(name = "{0} edits {1} ticket, priority change={2} → {3}")
    @MethodSource("editCases")
    void requireCanEdit(Role role, TicketStatus status, boolean changesPriority, ErrorCode expected) {
        AuthenticatedUser actor = actorFor(role);
        Ticket t = Fixtures.ticket(customer, status);
        assertOutcome(() -> policy.requireCanEdit(actor, t, changesPriority), expected);
    }

    // ───────────── assign ─────────────

    @Test
    void requireCanAssign_customer403_staffOk_closed409() {
        Ticket open = Fixtures.ticket(customer);
        assertOutcome(() -> policy.requireCanAssign(Fixtures.actor(customer), open), ErrorCode.ACCESS_DENIED);
        assertOutcome(() -> policy.requireCanAssign(Fixtures.actor(agent), open), null);
        assertOutcome(() -> policy.requireCanAssign(Fixtures.actor(admin), open), null);
        Ticket closed = Fixtures.ticket(customer, TicketStatus.CLOSED);
        assertOutcome(() -> policy.requireCanAssign(Fixtures.actor(agent), closed), ErrorCode.TICKET_CLOSED);
    }

    // ───────────── transition ─────────────

    @Test
    void requireCanTransition_delegatesToStateMachine() {
        Ticket inProgress = Fixtures.ticket(customer, TicketStatus.IN_PROGRESS);
        assertOutcome(() -> policy.requireCanTransition(Fixtures.actor(customer), inProgress, TicketStatus.CLOSED), null);
        assertOutcome(() -> policy.requireCanTransition(Fixtures.actor(customer), inProgress, TicketStatus.RESOLVED), ErrorCode.ACCESS_DENIED);
        assertOutcome(() -> policy.requireCanTransition(Fixtures.actor(agent), inProgress, TicketStatus.IN_PROGRESS), ErrorCode.INVALID_STATUS_TRANSITION);
    }

    // ───────────── comment (FR-21, BR-6) ─────────────

    @Test
    void requireCanComment_rules() {
        Ticket open = Fixtures.ticket(customer);
        Ticket closed = Fixtures.ticket(customer, TicketStatus.CLOSED);
        assertOutcome(() -> policy.requireCanComment(Fixtures.actor(customer), open, CommentVisibility.PUBLIC), null);
        assertOutcome(() -> policy.requireCanComment(Fixtures.actor(customer), open, CommentVisibility.INTERNAL), ErrorCode.ACCESS_DENIED);
        assertOutcome(() -> policy.requireCanComment(Fixtures.actor(agent), open, CommentVisibility.INTERNAL), null);
        assertOutcome(() -> policy.requireCanComment(Fixtures.actor(agent), closed, CommentVisibility.PUBLIC), ErrorCode.TICKET_CLOSED);
        assertOutcome(() -> policy.requireCanComment(Fixtures.actor(customer), closed, CommentVisibility.INTERNAL), ErrorCode.ACCESS_DENIED);
    }

    @Test
    void canSeeInternalNotes_onlyStaff() {
        assertThat(policy.canSeeInternalNotes(Fixtures.actor(agent))).isTrue();
        assertThat(policy.canSeeInternalNotes(Fixtures.actor(admin))).isTrue();
        assertThat(policy.canSeeInternalNotes(Fixtures.actor(customer))).isFalse();
    }

    // ───────────── permissions for the UI ─────────────

    @Test
    void permissionsFor_eachRole() {
        Ticket open = Fixtures.ticket(customer, TicketStatus.OPEN);
        Ticket inProgress = Fixtures.ticket(customer, TicketStatus.IN_PROGRESS);
        Ticket closed = Fixtures.ticket(customer, TicketStatus.CLOSED);

        assertThat(policy.permissionsFor(Fixtures.actor(agent), inProgress))
                .isEqualTo(new TicketPermissions(true, true, true, true, true));
        assertThat(policy.permissionsFor(Fixtures.actor(admin), open))
                .isEqualTo(new TicketPermissions(true, true, true, true, true));
        assertThat(policy.permissionsFor(Fixtures.actor(customer), open))
                .isEqualTo(new TicketPermissions(true, false, false, false, true));
        assertThat(policy.permissionsFor(Fixtures.actor(customer), inProgress))
                .isEqualTo(new TicketPermissions(false, false, false, false, true));
        assertThat(policy.permissionsFor(Fixtures.actor(agent), closed))
                .isEqualTo(new TicketPermissions(false, false, false, false, false));
    }

    private AuthenticatedUser actorFor(Role role) {
        return switch (role) {
            case ORG_ADMIN -> Fixtures.actor(admin);
            case AGENT -> Fixtures.actor(agent);
            case CUSTOMER -> Fixtures.actor(customer);
        };
    }

    private static void assertOutcome(Runnable action, ErrorCode expected) {
        if (expected == null) {
            assertThatCode(action::run).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(action::run).isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).code()).isEqualTo(expected);
        }
    }
}
