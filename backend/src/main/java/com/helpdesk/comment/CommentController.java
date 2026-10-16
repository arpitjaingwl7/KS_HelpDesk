package com.helpdesk.comment;

import com.helpdesk.comment.dto.CommentResponse;
import com.helpdesk.comment.dto.CreateCommentRequest;
import com.helpdesk.common.web.PageResponse;
import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/comments")
@Tag(name = "Comments", description = "Public replies and internal notes on a ticket")
@ApiResponse(responseCode = "404", description = "Ticket not visible to the caller")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    @Operation(summary = "List comments (oldest first)", description = "INTERNAL notes are never returned to customers.")
    @ApiResponse(responseCode = "200", description = "OK")
    public PageResponse<CommentResponse> list(@CurrentUser AuthenticatedUser actor, @PathVariable UUID ticketId,
                                              @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return commentService.list(actor, ticketId, pageable);
    }

    @PostMapping
    @Operation(summary = "Add a public reply or an internal note")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "403", description = "A customer tried to post an INTERNAL note")
    @ApiResponse(responseCode = "409", description = "TICKET_CLOSED")
    public ResponseEntity<CommentResponse> add(@CurrentUser AuthenticatedUser actor, @PathVariable UUID ticketId,
                                               @Valid @RequestBody CreateCommentRequest request) {
        CommentResponse created = commentService.add(actor, ticketId, request);
        return ResponseEntity.created(URI.create("/api/v1/tickets/" + ticketId + "/comments/" + created.id()))
                .body(created);
    }
}
