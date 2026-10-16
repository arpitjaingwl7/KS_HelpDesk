package com.helpdesk.security;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings ({@code helpdesk.jwt.*}). The secret comes from {@code JWT_SECRET}; the app refuses
 * to start when it is missing or shorter than 32 bytes (256 bits) — NFR-1.
 */
@ConfigurationProperties("helpdesk.jwt")
public record JwtProperties(String secret, long ttlMinutes, String issuer) {

    public static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and at least " + MIN_SECRET_BYTES + " bytes long (e.g. `openssl rand -base64 48`)");
        }
        if (ttlMinutes <= 0) {
            ttlMinutes = 60;
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "helpdesk";
        }
    }

    public byte[] secretBytes() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
