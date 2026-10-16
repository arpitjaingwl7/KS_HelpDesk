package com.helpdesk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.helpdesk.auth.dto.AuthResponse;
import com.helpdesk.auth.dto.LoginRequest;
import com.helpdesk.auth.dto.RegisterOrganizationRequest;
import com.helpdesk.common.error.ApiException;
import com.helpdesk.common.error.ErrorCode;
import com.helpdesk.organization.Organization;
import com.helpdesk.organization.OrganizationService;
import com.helpdesk.security.JwtProperties;
import com.helpdesk.security.JwtTokenService;
import com.helpdesk.security.Role;
import com.helpdesk.support.Fixtures;
import com.helpdesk.user.User;
import com.helpdesk.user.UserService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    static final String SECRET = "unit-test-secret-0123456789-abcdefghijklmnop";

    @Mock
    OrganizationService organizationService;
    @Mock
    UserService userService;

    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    final SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(), "HmacSHA256");
    AuthService service;

    final Organization acme = new Organization("Acme Corp", "acme");
    User alice;

    @BeforeEach
    void setUp() {
        JwtTokenService tokens = new JwtTokenService(new NimbusJwtEncoder(new ImmutableSecret<>(key)),
                new JwtProperties(SECRET, 60, "helpdesk"), Clock.fixed(Fixtures.NOW, ZoneOffset.UTC));
        service = new AuthService(organizationService, userService, tokens, encoder);
        alice = Fixtures.user(acme.getId(), Role.ORG_ADMIN, "alice@acme.com");
        ReflectionTestUtils.setField(alice, "passwordHash", encoder.encode("S3cure!pass"));
        lenient().when(organizationService.findBySlug("acme")).thenReturn(Optional.of(acme));
    }

    @Test
    void login_valid_returnsTokenWithClaims_andRecordsLogin() {
        when(userService.findByEmail(acme.getId(), "alice@acme.com")).thenReturn(Optional.of(alice));

        AuthResponse response = service.login(new LoginRequest("acme", "alice@acme.com", "S3cure!pass"));

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(token -> OAuth2TokenValidatorResult.success());   // fixed clock is in the past
        Jwt jwt = decoder.decode(response.accessToken());
        assertThat(jwt.getExpiresAt()).isEqualTo(Fixtures.NOW.plusSeconds(3600));
        assertThat(jwt.getSubject()).isEqualTo(alice.getId().toString());
        assertThat(jwt.getClaimAsString("org")).isEqualTo(acme.getId().toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ORG_ADMIN");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("helpdesk");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().organization().slug()).isEqualTo("acme");
        verify(userService).recordLogin(alice);
    }

    @Test
    void login_wrongPassword_invalidCredentials() {
        when(userService.findByEmail(acme.getId(), "alice@acme.com")).thenReturn(Optional.of(alice));
        assertInvalidCredentials(new LoginRequest("acme", "alice@acme.com", "wrong-password"));
    }

    @Test
    void login_unknownOrg_invalidCredentials() {
        when(organizationService.findBySlug("nope")).thenReturn(Optional.empty());
        assertInvalidCredentials(new LoginRequest("nope", "alice@acme.com", "S3cure!pass"));
    }

    @Test
    void login_unknownEmail_invalidCredentials() {
        when(userService.findByEmail(acme.getId(), "ghost@acme.com")).thenReturn(Optional.empty());
        assertInvalidCredentials(new LoginRequest("acme", "ghost@acme.com", "S3cure!pass"));
    }

    @Test
    void login_inactiveUser_invalidCredentials() {
        alice.setActive(false);
        when(userService.findByEmail(acme.getId(), "alice@acme.com")).thenReturn(Optional.of(alice));
        assertInvalidCredentials(new LoginRequest("acme", "alice@acme.com", "S3cure!pass"));
    }

    @Test
    void registerOrganization_createsOrgAndAdmin() {
        Organization globex = new Organization("Globex", "globex");
        User gina = Fixtures.user(globex.getId(), Role.ORG_ADMIN, "gina@globex.com");
        when(organizationService.register("Globex", "globex")).thenReturn(globex);
        when(userService.createInitialAdmin(globex.getId(), "Gina", "gina@globex.com", "S3cure!pass")).thenReturn(gina);

        AuthResponse response = service.registerOrganization(
                new RegisterOrganizationRequest("Globex", "globex", "Gina", "gina@globex.com", "S3cure!pass"));

        assertThat(response.user().role()).isEqualTo(Role.ORG_ADMIN);
        assertThat(response.user().organization().slug()).isEqualTo("globex");
    }

    @Test
    void registerOrganization_duplicateSlug_propagates409() {
        when(organizationService.register("Acme", "acme"))
                .thenThrow(new ApiException(ErrorCode.SLUG_ALREADY_EXISTS, "taken"));
        assertThatThrownBy(() -> service.registerOrganization(
                new RegisterOrganizationRequest("Acme", "acme", "A", "a@a.com", "S3cure!pass")))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.SLUG_ALREADY_EXISTS);
        verify(userService, never()).createInitialAdmin(any(), any(), any(), any());
    }

    private void assertInvalidCredentials(LoginRequest request) {
        assertThatThrownBy(() -> service.login(request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
                    assertThat(e.getMessage()).isEqualTo("Organization, email or password is incorrect");
                });
        verify(userService, never()).recordLogin(any());
    }
}
