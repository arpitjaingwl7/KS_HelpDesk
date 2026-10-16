package com.helpdesk.user;

import com.helpdesk.common.web.PageResponse;
import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.security.CurrentUser;
import com.helpdesk.security.Role;
import com.helpdesk.user.dto.CreateUserRequest;
import com.helpdesk.user.dto.UpdateUserRequest;
import com.helpdesk.user.dto.UserDetail;
import com.helpdesk.user.dto.UserSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ORG_ADMIN')")
@Tag(name = "Users", description = "User management inside the caller's organization (ORG_ADMIN only)")
@ApiResponse(responseCode = "403", description = "Caller is not an ORG_ADMIN")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List and search users", description = "`q` searches name and email. Sort fields: fullName, email, role, createdAt, active.")
    @ApiResponse(responseCode = "200", description = "OK")
    public PageResponse<UserSummary> list(@CurrentUser AuthenticatedUser actor,
                                          @RequestParam(required = false) String q,
                                          @RequestParam(required = false) Role role,
                                          @RequestParam(required = false) Boolean active,
                                          @ParameterObject Pageable pageable) {
        return userService.list(actor, q, role, active, pageable);
    }

    @PostMapping
    @Operation(summary = "Create a user (agent, customer or admin)")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_EXISTS")
    public ResponseEntity<UserDetail> create(@CurrentUser AuthenticatedUser actor,
                                             @Valid @RequestBody CreateUserRequest request) {
        UserDetail created = userService.create(actor, request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get a user")
    @ApiResponse(responseCode = "404", description = "No such user in your organization")
    @ApiResponse(responseCode = "200", description = "OK")
    public UserDetail get(@CurrentUser AuthenticatedUser actor, @Parameter(description = "User id") @PathVariable UUID userId) {
        return userService.get(actor, userId);
    }

    @PatchMapping("/{userId}")
    @Operation(summary = "Change name, role or active flag", description = "Requires the current `version`.")
    @ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION, LAST_ADMIN or SELF_DEACTIVATION")
    @ApiResponse(responseCode = "200", description = "OK")
    public UserDetail update(@CurrentUser AuthenticatedUser actor, @PathVariable UUID userId,
                             @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(actor, userId, request);
    }
}
