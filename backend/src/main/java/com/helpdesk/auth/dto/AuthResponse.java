package com.helpdesk.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthResponse(
        String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Seconds until the token expires", example = "3600") long expiresIn,
        CurrentUserResponse user) {

    public static AuthResponse bearer(String token, long expiresIn, CurrentUserResponse user) {
        return new AuthResponse(token, "Bearer", expiresIn, user);
    }
}
