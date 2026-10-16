package com.helpdesk.history.dto;

import com.helpdesk.history.HistoryEventType;
import com.helpdesk.user.dto.UserRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record HistoryEntryResponse(
        UUID id,
        HistoryEventType eventType,
        @Schema(description = "A string (status / priority name), or a UserRef {id, fullName} for ASSIGNEE_CHANGED", nullable = true,
                oneOf = {String.class, UserRef.class})
        Object oldValue,
        @Schema(description = "A string (status / priority name), or a UserRef {id, fullName} for ASSIGNEE_CHANGED", nullable = true,
                oneOf = {String.class, UserRef.class})
        Object newValue,
        UserRef actor,
        Instant createdAt) {
}
