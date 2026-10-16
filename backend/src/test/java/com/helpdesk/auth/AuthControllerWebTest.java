package com.helpdesk.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helpdesk.auth.dto.AuthResponse;
import com.helpdesk.auth.dto.CurrentUserResponse;
import com.helpdesk.common.error.ApiException;
import com.helpdesk.common.error.ErrorCode;
import com.helpdesk.organization.dto.OrganizationRef;
import com.helpdesk.security.PrincipalLoader;
import com.helpdesk.security.Role;
import com.helpdesk.support.WebSliceTestConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(WebSliceTestConfig.class)
@ActiveProfiles("test")
class AuthControllerWebTest {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    AuthService authService;
    @MockitoBean
    PrincipalLoader principalLoader;

    static AuthResponse response() {
        return AuthResponse.bearer("token", 3600, new CurrentUserResponse(UUID.randomUUID(), "Alice", "alice@acme.com",
                Role.ORG_ADMIN, new OrganizationRef(UUID.randomUUID(), "Acme", "acme")));
    }

    @Test
    void login_isPublic() throws Exception {
        when(authService.login(any())).thenReturn(response());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationSlug\":\"ACME\",\"email\":\"alice@acme.com\",\"password\":\"S3cure!pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.organization.slug").value("acme"));
    }

    @Test
    void login_badCredentials_returns401() throws Exception {
        when(authService.login(any())).thenThrow(new ApiException(ErrorCode.INVALID_CREDENTIALS, "Organization, email or password is incorrect"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationSlug\":\"acme\",\"email\":\"alice@acme.com\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void login_missingFields_returns400() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void register_invalidSlug_returns400() throws Exception {
        mvc.perform(post("/api/v1/auth/register-organization").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationName\":\"Acme\",\"organizationSlug\":\"-Bad Slug-\",\"adminFullName\":\"A\","
                                + "\"adminEmail\":\"a@acme.com\",\"adminPassword\":\"S3cure!pass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("organizationSlug"));
    }

    @Test
    void register_returns201() throws Exception {
        when(authService.registerOrganization(any())).thenReturn(response());
        mvc.perform(post("/api/v1/auth/register-organization").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationName\":\"Acme\",\"organizationSlug\":\"acme\",\"adminFullName\":\"Alice\","
                                + "\"adminEmail\":\"alice@acme.com\",\"adminPassword\":\"S3cure!pass\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/organization"));
    }

    @Test
    void me_requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }
}
