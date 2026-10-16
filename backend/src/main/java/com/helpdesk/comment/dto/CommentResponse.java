package com.helpdesk.comment.dto;

import com.helpdesk.ticket.domain.CommentVisibility;
import java.time.Instant;
import java.util.UUID;

public record CommentResponse(UUID id, String body, CommentVisibility visibility, CommentAuthor author, Instant createdAt) {
}
