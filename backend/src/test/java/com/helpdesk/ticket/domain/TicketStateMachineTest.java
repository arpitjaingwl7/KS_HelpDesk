package com.helpdesk.ticket.domain;

import static com.helpdesk.ticket.domain.TicketStatus.CLOSED;
import static com.helpdesk.ticket.domain.TicketStatus.IN_PROGRESS;
import static com.helpdesk.ticket.domain.TicketStatus.ON_HOLD;
import static com.helpdesk.ticket.domain.TicketStatus.OPEN;
import static com.helpdesk.ticket.domain.TicketStatus.RESOLVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.helpdesk.common.error.ApiException;
import com.helpdesk.common.error.ErrorCode;
import com.helpdesk.security.Role;
import com.helpdesk.support.Fixtures;
import com.helpdesk.user.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/** Exhaustive coverage of the matrix in docs/03 §3. */
class TicketStateMachineTest {

    /** The staff matrix, written out independently of the implementation. */
    private static final Set<List<TicketStatus>> STAFF_ALLOWED = Set.of(
            List.of(OPEN, IN_PROGRESS), List.of(OPEN, ON_HOLD), List.of(OPEN, RESOLVED), List.of(OPEN, CLOSED),
            List.of(IN_PROGRESS, OPEN), List.of(IN_PROGRESS, ON_HOLD), List.of(IN_PROGRESS, RESOLVED), List.of(IN_PROGRESS, CLOSED),
            List.of(ON_HOLD, IN_PROGRESS), List.of(ON_HOLD, RESOLVED), List.of(ON_HOLD, CLOSED),
            List.of(RESOLVED, OPEN), List.of(RESOLVED, CLOSED),
            List.of(CLOSED, OPEN));

    private static final Set<List<TicketStatus>> CUSTOMER_ALLOWED = Set.of(
            List.of(OPEN, CLOSED), List.of(IN_PROGRESS, CLOSED), List.of(ON_HOLD, CLOSED),
            List.of(RESOLVED, CLOSED), List.of(RESOLVED, OPEN));

    static Stream<Arguments> allPairs() {
        List<Arguments> pairs = new ArrayList<>();
        for (TicketStatus from : TicketStatus.values()) {
            for (TicketStatus to : TicketStatus.values()) {
                pairs.add(Arguments.of(from, to));
            }
        }
        return pairs.stream();
    }

    @ParameterizedTest(name = "staff {0} → {1}")
    @MethodSource("allPairs")
    void validate_staff_matchesMatrix(TicketStatus from, TicketStatus to) {
        for (Role role : List.of(Role.AGENT, Role.ORG_ADMIN)) {
            if (STAFF_ALLOWED.contains(List.of(from, to))) {
                assertThatCode(() -> TicketStateMachine.validate(role, from, to)).doesNotThrowAnyException();
            } else {
                assertThatThrownBy(() -> TicketStateMachine.validate(role, from, to))
                        .isInstanceOf(ApiException.class)
                        .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
            }
        }
    }

    @ParameterizedTest(name = "customer {0} → {1}")
    @MethodSource("allPairs")
    void validate_customer_matchesMatrix(TicketStatus from, TicketStatus to) {
        List<TicketStatus> pair = List.of(from, to);
        if (CUSTOMER_ALLOWED.contains(pair)) {
            assertThatCode(() -> TicketStateMachine.validate(Role.CUSTOMER, from, to)).doesNotThrowAnyException();
        } else {
            ErrorCode expected = STAFF_ALLOWED.contains(pair) ? ErrorCode.ACCESS_DENIED : ErrorCode.INVALID_STATUS_TRANSITION;
            assertThatThrownBy(() -> TicketStateMachine.validate(Role.CUSTOMER, from, to))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).code()).isEqualTo(expected);
        }
    }

    @ParameterizedTest
    @EnumSource(TicketStatus.class)
    void sameStatus_isNeverATransition(TicketStatus status) {
        assertThat(TicketStateMachine.exists(status, status)).isFalse();
    }

    @Test
    void customer_cannotReopenClosedTicket_butStaffCan() {
        assertThat(TicketStateMachine.isAllowed(Role.CUSTOMER, CLOSED, OPEN)).isFalse();
        assertThat(TicketStateMachine.isAllowed(Role.AGENT, CLOSED, OPEN)).isTrue();
    }

    @Test
    void allowedTargets_areOrderedAndRoleSpecific() {
        assertThat(TicketStateMachine.allowedTargets(Role.AGENT, IN_PROGRESS)).containsExactly(OPEN, ON_HOLD, RESOLVED, CLOSED);
        assertThat(TicketStateMachine.allowedTargets(Role.CUSTOMER, RESOLVED)).containsExactly(OPEN, CLOSED);
        assertThat(TicketStateMachine.allowedTargets(Role.CUSTOMER, CLOSED)).isEmpty();
    }

    @Nested
    class SideEffects {

        private final Instant now = Fixtures.NOW;
        private final User customer = Fixtures.user(UUID.randomUUID(), Role.CUSTOMER);

        @Test
        void resolved_setsResolvedAt() {
            Ticket t = Fixtures.ticket(customer, IN_PROGRESS);
            t.changeStatus(RESOLVED, now);
            assertThat(t.getResolvedAt()).isEqualTo(now);
            assertThat(t.getClosedAt()).isNull();
        }

        @Test
        void closed_setsClosedAt_andResolvedAtWhenEmpty() {
            Ticket t = Fixtures.ticket(customer, OPEN);
            t.changeStatus(CLOSED, now);
            assertThat(t.getClosedAt()).isEqualTo(now);
            assertThat(t.getResolvedAt()).isEqualTo(now);
        }

        @Test
        void closed_keepsExistingResolvedAt() {
            Ticket t = Fixtures.ticket(customer, IN_PROGRESS);
            Instant earlier = now.minusSeconds(3600);
            t.changeStatus(RESOLVED, earlier);
            t.changeStatus(CLOSED, now);
            assertThat(t.getResolvedAt()).isEqualTo(earlier);
            assertThat(t.getClosedAt()).isEqualTo(now);
        }

        @Test
        void reopen_clearsBothTimestamps() {
            Ticket t = Fixtures.ticket(customer, OPEN);
            t.changeStatus(CLOSED, now);
            t.changeStatus(OPEN, now.plusSeconds(10));
            assertThat(t.getResolvedAt()).isNull();
            assertThat(t.getClosedAt()).isNull();
            assertThat(t.getStatus()).isEqualTo(OPEN);
        }

        @Test
        void onHold_changesNoTimestamps() {
            Ticket t = Fixtures.ticket(customer, OPEN);
            t.changeStatus(ON_HOLD, now);
            assertThat(t.getResolvedAt()).isNull();
            assertThat(t.getClosedAt()).isNull();
        }

        @Test
        void entity_rejectsNonExistentTransition() {
            Ticket t = Fixtures.ticket(customer, ON_HOLD);
            assertThatThrownBy(() -> t.changeStatus(OPEN, now))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
        }
    }
}
