package com.helpdesk.ticket.web;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helpdesk.common.error.ApiException;
import com.helpdesk.common.error.ErrorCode;
import com.helpdesk.common.web.PageResponse;
import com.helpdesk.security.PrincipalLoader;
import com.helpdesk.security.Role;
import com.helpdesk.support.WebSliceTestConfig;
import com.helpdesk.support.WithAuthenticatedUser;
import com.helpdesk.ticket.domain.TicketPermissions;
import com.helpdesk.ticket.domain.TicketPriority;
import com.helpdesk.ticket.domain.TicketStatus;
import com.helpdesk.ticket.repository.TicketSearchCriteria;
import com.helpdesk.ticket.service.TicketQueryService;
import com.helpdesk.ticket.service.TicketService;
import com.helpdesk.ticket.web.dto.TicketDetail;
import com.helpdesk.ticket.web.dto.TicketUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TicketController.class)
@Import(WebSliceTestConfig.class)
@ActiveProfiles("test")
class TicketControllerWebTest {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    TicketService ticketService;
    @MockitoBean
    TicketQueryService ticketQueryService;
    @MockitoBean
    PrincipalLoader principalLoader;

    static final UUID TICKET_ID = UUID.fromString("0192a7f0-0000-7000-8000-000000000042");

    static TicketDetail detail() {
        return new TicketDetail(TICKET_ID, 42, "Cannot reset password", "Body", TicketStatus.OPEN, TicketPriority.HIGH,
                new TicketUser(UUID.randomUUID(), "Carol", "carol@example.com"), null, Instant.now(), Instant.now(),
                null, null, 0, List.of(TicketStatus.IN_PROGRESS), new TicketPermissions(true, true, true, true, true));
    }

    @Test
    void noToken_returns401ProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/tickets"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void invalidToken_returns401() throws Exception {
        mvc.perform(get("/api/v1/tickets").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @WithAuthenticatedUser(role = Role.CUSTOMER)
    void blankSubject_returns400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"  \",\"description\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("subject")));
        verify(ticketService, never()).create(any(), any());
    }

    @Test
    @WithAuthenticatedUser(role = Role.CUSTOMER)
    void invalidEnum_returns400MalformedRequest() throws Exception {
        mvc.perform(post("/api/v1/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Valid subject\",\"description\":\"x\",\"priority\":\"CRITICAL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("priority"));
    }

    @Test
    @WithAuthenticatedUser(role = Role.CUSTOMER)
    void malformedJson_returns400() throws Exception {
        mvc.perform(post("/api/v1/tickets").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @WithAuthenticatedUser(role = Role.CUSTOMER)
    void create_returns201WithLocation() throws Exception {
        when(ticketService.create(any(), any())).thenReturn(detail());
        mvc.perform(post("/api/v1/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Cannot reset password\",\"description\":\"Body\",\"organizationId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/tickets/" + TICKET_ID)))
                .andExpect(jsonPath("$.number").value(42))
                .andExpect(jsonPath("$.permissions.canAssign").value(true))
                .andExpect(jsonPath("$.assignee").doesNotExist());
    }

    @Test
    @WithAuthenticatedUser(role = Role.CUSTOMER)
    void customer_assigning_returns403() throws Exception {
        mvc.perform(put("/api/v1/tickets/{id}/assignee", TICKET_ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verify(ticketService, never()).assign(any(), any(), any());
    }

    @Test
    @WithAuthenticatedUser(role = Role.CUSTOMER)
    void customer_stats_returns403() throws Exception {
        mvc.perform(get("/api/v1/tickets/stats")).andExpect(status().isForbidden());
    }

    @Test
    @WithAuthenticatedUser(role = Role.AGENT)
    void search_bindsFiltersAndCapsPageSize() throws Exception {
        when(ticketQueryService.search(any(), any(), any())).thenReturn(
                new PageResponse<>(List.of(), new PageResponse.PageMetadata(0, 100, 0, 0)));

        mvc.perform(get("/api/v1/tickets")
                        .param("status", "OPEN,IN_PROGRESS").param("priority", "HIGH").param("priority", "URGENT")
                        .param("unassigned", "true").param("q", "password").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(100));

        ArgumentCaptor<TicketSearchCriteria> criteria = ArgumentCaptor.forClass(TicketSearchCriteria.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(ticketQueryService).search(any(), criteria.capture(), pageable.capture());
        org.assertj.core.api.Assertions.assertThat(criteria.getValue().statuses())
                .containsExactly(TicketStatus.OPEN, TicketStatus.IN_PROGRESS);
        org.assertj.core.api.Assertions.assertThat(criteria.getValue().priorities())
                .containsExactly(TicketPriority.HIGH, TicketPriority.URGENT);
        org.assertj.core.api.Assertions.assertThat(criteria.getValue().unassigned()).isTrue();
        org.assertj.core.api.Assertions.assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @WithAuthenticatedUser(role = Role.AGENT)
    void search_badStatusValue_returns400() throws Exception {
        mvc.perform(get("/api/v1/tickets").param("status", "DONE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @WithAuthenticatedUser(role = Role.AGENT)
    void serviceErrors_mapToStatusCodes() throws Exception {
        when(ticketQueryService.get(any(), eq(TICKET_ID))).thenThrow(ApiException.notFound("Ticket", TICKET_ID));
        mvc.perform(get("/api/v1/tickets/{id}", TICKET_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.type").value("https://helpdesk.local/problems/resource-not-found"));

        when(ticketService.changeStatus(any(), eq(TICKET_ID), any(), any()))
                .thenThrow(new ApiException(ErrorCode.INVALID_STATUS_TRANSITION, "nope"));
        mvc.perform(put("/api/v1/tickets/{id}/status", TICKET_ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @WithAuthenticatedUser(role = Role.AGENT)
    void unexpectedError_returnsGeneric500WithoutStackTrace() throws Exception {
        when(ticketQueryService.get(any(), any())).thenThrow(new IllegalStateException("db password is hunter2"));
        mvc.perform(get("/api/v1/tickets/{id}", TICKET_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @Test
    @WithAuthenticatedUser(role = Role.AGENT)
    void patch_withoutAnyField_returns400() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/tickets/{id}", TICKET_ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithAuthenticatedUser(role = Role.AGENT)
    void wrongContentType_returns415() throws Exception {
        mvc.perform(post("/api/v1/tickets").contentType(MediaType.TEXT_PLAIN).content("hi"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @WithAuthenticatedUser(role = Role.AGENT)
    void responses_carrySecurityHeaders() throws Exception {
        when(ticketQueryService.get(any(), any())).thenReturn(detail());
        mvc.perform(get("/api/v1/tickets/{id}", TICKET_ID))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
