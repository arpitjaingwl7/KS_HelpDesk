package com.helpdesk.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    @Test
    void missingOrShortSecret_refusesToStart() {
        assertThatThrownBy(() -> new JwtProperties(null, 60, "helpdesk")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProperties("too-short", 60, "helpdesk")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void defaults_applied() {
        JwtProperties p = new JwtProperties("x".repeat(32), 0, " ");
        assertThat(p.ttlMinutes()).isEqualTo(60);
        assertThat(p.issuer()).isEqualTo("helpdesk");
        assertThat(p.secretBytes()).hasSize(32);
    }
}
