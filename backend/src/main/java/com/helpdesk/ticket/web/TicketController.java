package com.helpdesk.ticket.web;

import com.helpdesk.common.web.PageResponse;
import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.security.CurrentUser;
import com.helpdesk.ticket.domain.TicketPriority;
import com.helpdesk.ticket.domain.TicketStatus;
import com.helpdesk.ticket.repository.TicketSearchCriteria;
import com.helpdesk.ticket.service.TicketQueryService;
import com.helpdesk.ticket.service.TicketService;
import com.helpdesk.ticket.web.dto.AssignTicketRequest;
import com.helpdesk.ticket.web.dto.ChangeStatusRequest;
import com.helpdesk.ticket.web.dto.CreateTicketRequest;
import com.helpdesk.ticket.web.dto.TicketDetail;
import com.helpdesk.ticket.web.dto.TicketStats;
import com.helpdesk.ticket.web.dto.TicketSummary;
import com.helpdesk.ticket.web.dto.UpdateTicketRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "Tickets", description = "Create, search, update, assign and move tickets through their workflow")
@ApiResponse(responseCode = "401", description = "Missing or invalid token")
public class TicketController {

    private final TicketService ticketService;
    private final TicketQueryService ticketQueryService;

    public TicketController(TicketService ticketService, TicketQueryService ticketQueryService) {
        this.ticketService = ticketService;
        this.ticketQueryService = ticketQueryService;
    }

    @PostMapping
    @Operation(summary = "Create a ticket", description = "Status starts as OPEN; priority defaults to MEDIUM. "
            + "Agents and admins may set `requesterId` to raise a ticket for a customer.")
    @ApiResponse(responseCode = "201", description = "Created; Location header points to the ticket")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED or INVALID_REQUESTER")
    @ApiResponse(responseCode = "403", description = "A customer sent requesterId")
    public ResponseEntity<TicketDetail> create(@CurrentUser AuthenticatedUser actor,
                                               @Valid @RequestBody CreateTicketRequest request) {
        TicketDetail created = ticketService.create(actor, request);
        return ResponseEntity.created(URI.create("/api/v1/tickets/" + created.id())).body(created);
    }

    @GetMapping
    @Operation(summary = "Search, filter and paginate tickets",
            description = "Customers only ever see their own tickets. Sort fields: createdAt, updatedAt, number, priority, status. "
                    + "Default sort: createdAt,desc. Max page size 100.")
    @ApiResponse(responseCode = "400", description = "INVALID_SORT, MALFORMED_REQUEST or VALIDATION_FAILED")
    @ApiResponse(responseCode = "200", description = "OK")
    public PageResponse<TicketSummary> search(
            @CurrentUser AuthenticatedUser actor,
            @Parameter(description = "Matches subject or description (case-insensitive); a number also matches the ticket number")
            @RequestParam(required = false) String q,
            @Parameter(description = "One or many; repeat the parameter or use a comma list")
            @RequestParam(required = false) List<TicketStatus> status,
            @RequestParam(required = false) List<TicketPriority> priority,
            @Parameter(description = "Ignored for customers") @RequestParam(required = false) UUID assigneeId,
            @Parameter(description = "Only unassigned tickets; cannot be combined with assigneeId")
            @RequestParam(required = false, defaultValue = "false") boolean unassigned,
            @Parameter(description = "Agents and admins only; forced to the caller for customers")
            @RequestParam(required = false) UUID requesterId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @ParameterObject Pageable pageable) {
        TicketSearchCriteria criteria = new TicketSearchCriteria(q, status, priority, assigneeId, unassigned,
                requesterId, createdFrom, createdTo);
        return ticketQueryService.search(actor, criteria, pageable);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ORG_ADMIN','AGENT')")
    @Operation(summary = "Ticket counters for a dashboard (ORG_ADMIN, AGENT)")
    @ApiResponse(responseCode = "200", description = "OK")
    public TicketStats stats(@CurrentUser AuthenticatedUser actor) {
        return ticketQueryService.stats(actor);
    }

    @GetMapping("/{ticketId}")
    @Operation(summary = "Get a ticket", description = "Includes `allowedTransitions` and `permissions` for the caller.")
    @ApiResponse(responseCode = "404", description = "No such ticket, another organization's, or another customer's")
    @ApiResponse(responseCode = "200", description = "OK")
    public TicketDetail get(@CurrentUser AuthenticatedUser actor, @PathVariable UUID ticketId) {
        return ticketQueryService.get(actor, ticketId);
    }

    @PatchMapping("/{ticketId}")
    @Operation(summary = "Update subject, description or priority",
            description = "Requires `version`. Customers may edit only subject/description of their own OPEN ticket.")
    @ApiResponse(responseCode = "403", description = "Field or state not editable by this role")
    @ApiResponse(responseCode = "404", description = "Ticket not visible")
    @ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION or TICKET_CLOSED")
    @ApiResponse(responseCode = "200", description = "OK")
    public TicketDetail update(@CurrentUser AuthenticatedUser actor, @PathVariable UUID ticketId,
                               @Valid @RequestBody UpdateTicketRequest request) {
        return ticketService.update(actor, ticketId, request);
    }

    @PutMapping("/{ticketId}/assignee")
    @PreAuthorize("hasAnyRole('ORG_ADMIN','AGENT')")
    @Operation(summary = "Assign the ticket (ORG_ADMIN, AGENT)",
            description = "Assignee must be an active AGENT or ORG_ADMIN in your organization. Re-assigning to the current assignee is a no-op.")
    @ApiResponse(responseCode = "400", description = "INVALID_ASSIGNEE")
    @ApiResponse(responseCode = "403", description = "Caller is a CUSTOMER")
    @ApiResponse(responseCode = "409", description = "TICKET_CLOSED")
    @ApiResponse(responseCode = "200", description = "OK")
    public TicketDetail assign(@CurrentUser AuthenticatedUser actor, @PathVariable UUID ticketId,
                               @Valid @RequestBody AssignTicketRequest request) {
        return ticketService.assign(actor, ticketId, request.assigneeId());
    }

    @DeleteMapping("/{ticketId}/assignee")
    @PreAuthorize("hasAnyRole('ORG_ADMIN','AGENT')")
    @Operation(summary = "Unassign the ticket (ORG_ADMIN, AGENT)", description = "Returns 200 with the ticket so the client gets the new version.")
    @ApiResponse(responseCode = "200", description = "OK")
    public TicketDetail unassign(@CurrentUser AuthenticatedUser actor, @PathVariable UUID ticketId) {
        return ticketService.unassign(actor, ticketId);
    }

    @PutMapping("/{ticketId}/status")
    @Operation(summary = "Change status (including close and reopen)",
            description = "Must follow the state machine. Customers may close their own ticket and reopen it when RESOLVED.")
    @ApiResponse(responseCode = "403", description = "The transition exists but this role may not make it")
    @ApiResponse(responseCode = "409", description = "INVALID_STATUS_TRANSITION")
    @ApiResponse(responseCode = "200", description = "OK")
    public TicketDetail changeStatus(@CurrentUser AuthenticatedUser actor, @PathVariable UUID ticketId,
                                     @Valid @RequestBody ChangeStatusRequest request) {
        return ticketService.changeStatus(actor, ticketId, request.status(), request.comment());
    }
}
