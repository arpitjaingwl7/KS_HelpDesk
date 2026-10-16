package com.helpdesk.history;

import com.helpdesk.history.dto.HistoryEntryResponse;
import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "History", description = "Audit trail of status, assignment and priority changes")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/api/v1/tickets/{ticketId}/history")
    @Operation(summary = "Ticket history (oldest first, at most 500 entries)")
    @ApiResponse(responseCode = "404", description = "Ticket not visible to the caller")
    @ApiResponse(responseCode = "200", description = "OK")
    public List<HistoryEntryResponse> list(@CurrentUser AuthenticatedUser actor, @PathVariable UUID ticketId) {
        return historyService.list(actor, ticketId);
    }
}
