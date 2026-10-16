package com.helpdesk.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.helpdesk.comment.dto.CommentResponse;
import com.helpdesk.comment.dto.CreateCommentRequest;
import com.helpdesk.common.error.ApiException;
import com.helpdesk.common.error.ErrorCode;
import com.helpdesk.security.Role;
import com.helpdesk.support.Fixtures;
import com.helpdesk.ticket.domain.CommentVisibility;
import com.helpdesk.ticket.domain.Ticket;
import com.helpdesk.ticket.domain.TicketAccessPolicy;
import com.helpdesk.ticket.domain.TicketStatus;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.user.User;
import com.helpdesk.user.UserService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    CommentRepository comments;
    @Mock
    TicketRepository tickets;
    @Mock
    UserService userService;

    CommentService service;

    final UUID orgId = UUID.randomUUID();
    final User customer = Fixtures.user(orgId, Role.CUSTOMER);
    final User agent = Fixtures.user(orgId, Role.AGENT);

    @BeforeEach
    void setUp() {
        service = new CommentService(comments, new TicketAccessPolicy(tickets), userService, new CommentMapperImpl());
    }

    private Ticket stored(Ticket t) {
        when(tickets.findByIdAndOrganizationId(t.getId(), orgId)).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    void customerPostingInternal_throws403() {
        Ticket t = stored(Fixtures.ticket(customer));
        assertThatThrownBy(() -> service.add(Fixtures.actor(customer), t.getId(),
                new CreateCommentRequest("secret", CommentVisibility.INTERNAL)))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.ACCESS_DENIED);
        verify(comments, never()).saveAndFlush(any());
    }

    @Test
    void customerPostingPublicOnOwnTicket_ok_defaultsToPublic() {
        Ticket t = stored(Fixtures.ticket(customer));
        when(userService.requireActor(Fixtures.actor(customer))).thenReturn(customer);
        when(comments.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        CommentResponse response = service.add(Fixtures.actor(customer), t.getId(), new CreateCommentRequest("  Any update?  ", null));

        assertThat(response.visibility()).isEqualTo(CommentVisibility.PUBLIC);
        assertThat(response.body()).isEqualTo("Any update?");
        assertThat(response.author().role()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void customerListing_usesPublicOnlyQuery() {
        Ticket t = stored(Fixtures.ticket(customer));
        when(comments.findByOrganizationIdAndTicketIdAndVisibility(eq(orgId), eq(t.getId()), eq(CommentVisibility.PUBLIC), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(Fixtures.actor(customer), t.getId(), PageRequest.of(0, 50));

        verify(comments).findByOrganizationIdAndTicketIdAndVisibility(eq(orgId), eq(t.getId()), eq(CommentVisibility.PUBLIC), any());
        verify(comments, never()).findByOrganizationIdAndTicketId(any(), any(), any());
    }

    @Test
    void staffListing_usesUnfilteredQuery_oldestFirst() {
        Ticket t = stored(Fixtures.ticket(customer));
        when(comments.findByOrganizationIdAndTicketId(eq(orgId), eq(t.getId()), any())).thenReturn(new PageImpl<>(List.of()));

        service.list(Fixtures.actor(agent), t.getId(), PageRequest.of(1, 10));

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(comments).findByOrganizationIdAndTicketId(eq(orgId), eq(t.getId()), page.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(1);
        assertThat(page.getValue().getSort()).isEqualTo(CommentService.THREAD_ORDER);
    }

    @Test
    void commentingOnClosedTicket_throws409() {
        Ticket t = stored(Fixtures.ticket(customer, TicketStatus.CLOSED));
        assertThatThrownBy(() -> service.add(Fixtures.actor(agent), t.getId(), new CreateCommentRequest("hi", null)))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.TICKET_CLOSED);
    }

    @Test
    void otherCustomersTicket_throws404() {
        User other = Fixtures.user(orgId, Role.CUSTOMER);
        Ticket t = stored(Fixtures.ticket(other));
        assertThatThrownBy(() -> service.list(Fixtures.actor(customer), t.getId(), PageRequest.of(0, 50)))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void addStatusComment_isAlwaysPublic() {
        Ticket t = Fixtures.ticket(customer, TicketStatus.CLOSED);
        when(userService.requireActor(Fixtures.actor(agent))).thenReturn(agent);
        service.addStatusComment(Fixtures.actor(agent), t, "Closing");
        ArgumentCaptor<TicketComment> saved = ArgumentCaptor.forClass(TicketComment.class);
        verify(comments).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getVisibility()).isEqualTo(CommentVisibility.PUBLIC);
        assertThat(saved.getValue().getOrganizationId()).isEqualTo(orgId);
        assertThat(saved.getValue().getTicketId()).isEqualTo(t.getId());
    }
}
