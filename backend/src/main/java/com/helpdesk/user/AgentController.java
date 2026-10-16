package com.helpdesk.user;

import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.security.CurrentUser;
import com.helpdesk.user.dto.UserOption;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lookups for UI pickers. Not paginated; at most 50 results. */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ORG_ADMIN','AGENT')")
@Tag(name = "Users", description = "User management inside the caller's organization (ORG_ADMIN only)")
@ApiResponse(responseCode = "403", description = "Caller is a CUSTOMER")
public class AgentController {

    private final UserService userService;

    public AgentController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/agents")
    @Operation(summary = "Active users tickets can be assigned to (AGENT and ORG_ADMIN)")
    @ApiResponse(responseCode = "200", description = "OK")
    public List<UserOption> agents(@CurrentUser AuthenticatedUser actor, @RequestParam(required = false) String q) {
        return userService.assignableAgents(actor, q);
    }

    @GetMapping("/customers")
    @Operation(summary = "Active customers, for raising a ticket on their behalf")
    @ApiResponse(responseCode = "200", description = "OK")
    public List<UserOption> customers(@CurrentUser AuthenticatedUser actor, @RequestParam(required = false) String q) {
        return userService.activeCustomers(actor, q);
    }
}
