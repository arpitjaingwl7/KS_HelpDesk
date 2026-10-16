package com.helpdesk.comment.dto;

import com.helpdesk.ticket.domain.CommentVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @Schema(example = "Any update?") @NotBlank @Size(max = 5_000) String body,
        @Schema(description = "Defaults to PUBLIC. INTERNAL is staff only", example = "PUBLIC") CommentVisibility visibility) {

    public CommentVisibility visibilityOrDefault() {
        return visibility == null ? CommentVisibility.PUBLIC : visibility;
    }
}
