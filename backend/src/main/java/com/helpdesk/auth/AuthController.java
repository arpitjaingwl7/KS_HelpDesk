package com.helpdesk.auth;

import com.helpdesk.auth.dto.AuthResponse;
import com.helpdesk.auth.dto.CurrentUserResponse;
import com.helpdesk.auth.dto.LoginRequest;
import com.helpdesk.auth.dto.RegisterOrganizationRequest;
import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Organization registration, login and the current user")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register-organization")
    @SecurityRequirements
    @Operation(summary = "Register a new organization and its first ORG_ADMIN, then log in")
    @ApiResponse(responseCode = "201", description = "Organization created; body is the same as login")
    @ApiResponse(responseCode = "409", description = "SLUG_ALREADY_EXISTS")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterOrganizationRequest request) {
        AuthResponse response = authService.registerOrganization(request);
        return ResponseEntity.created(URI.create("/api/v1/organization")).body(response);
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Exchange organization slug, email and password for a JWT")
    @ApiResponse(responseCode = "200", description = "Logged in")
    @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS — same response for every failure")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "The current user and their organization")
    public CurrentUserResponse me(@CurrentUser AuthenticatedUser actor) {
        return authService.me(actor);
    }
}
