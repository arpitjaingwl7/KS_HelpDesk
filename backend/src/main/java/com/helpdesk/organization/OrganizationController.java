package com.helpdesk.organization;

import com.helpdesk.organization.dto.OrganizationResponse;
import com.helpdesk.organization.dto.UpdateOrganizationRequest;
import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization")
@Tag(name = "Organization", description = "The caller's own organization")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    @Operation(summary = "Get the current organization")
    public OrganizationResponse get(@CurrentUser AuthenticatedUser actor) {
        return organizationService.get(actor);
    }

    @PatchMapping
    @PreAuthorize("hasRole('ORG_ADMIN')")
    @Operation(summary = "Rename the organization (ORG_ADMIN)", description = "The slug cannot be changed.")
    @ApiResponse(responseCode = "200", description = "Renamed")
    @ApiResponse(responseCode = "403", description = "Not an org admin")
    public OrganizationResponse rename(@CurrentUser AuthenticatedUser actor,
                                       @Valid @RequestBody UpdateOrganizationRequest request) {
        return organizationService.rename(actor, request.name());
    }
}
