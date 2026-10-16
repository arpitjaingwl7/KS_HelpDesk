package com.helpdesk.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.helpdesk.common.error.ApiException;
import com.helpdesk.common.error.ErrorCode;
import com.helpdesk.organization.OrganizationController;
import com.helpdesk.organization.OrganizationService;
import com.helpdesk.organization.dto.OrganizationResponse;
import com.helpdesk.security.PrincipalLoader;
import com.helpdesk.security.Role;
import com.helpdesk.support.WebSliceTestConfig;
import com.helpdesk.support.WithAuthenticatedUser;
import com.helpdesk.user.dto.UserDetail;
import com.helpdesk.user.dto.UserOption;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({UserController.class, AgentController.class, OrganizationController.class})
@Import(WebSliceTestConfig.class)
@ActiveProfiles("test")
class UserControllerWebTest {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    UserService userService;
    @MockitoBean
    OrganizationService organizationService;
    @MockitoBean
    PrincipalLoader principalLoader;

    @Test
    @WithAuthenticatedUser(role = Role.AGENT)
    void agent_onUsers_returns403() throws Exception {
        mvc.perform(get("/api/v1/users")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @WithAuthenticatedUser(role = Role.CUSTOMER)
    void customer_onUsersAgentsCustomers_returns403() throws Exception {
        mvc.perform(get("/api/v1/users")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/agents")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/customers")).andExpect(status().isForbidden());
    }

    @Test
    @WithAuthenticatedUser(role = Role.AGENT)
    void agent_canListAgents() throws Exception {
        when(userService.assignableAgents(any(), any())).thenReturn(List.of(new UserOption(UUID.randomUUID(), "Bob", "bob@a.test", Role.AGENT)));
        mvc.perform(get("/api/v1/agents")).andExpect(status().isOk()).andExpect(jsonPath("$[0].fullName").value("Bob"));
    }

    @Test
    @WithAuthenticatedUser(role = Role.ORG_ADMIN)
    void admin_createUser_returns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.create(any(), any())).thenReturn(new UserDetail(id, "Bob", "bob@a.test", Role.AGENT, true, Instant.now(), null, 0));
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Bob\",\"email\":\"bob@a.test\",\"password\":\"Initial#123\",\"role\":\"AGENT\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/users/" + id))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    @WithAuthenticatedUser(role = Role.ORG_ADMIN)
    void admin_createUser_invalid_returns400_withoutEchoingPassword() throws Exception {
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Bob\",\"email\":\"not-an-email\",\"password\":\"short\",\"role\":\"AGENT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field == 'password')].rejectedValue").doesNotExist());
    }

    @Test
    @WithAuthenticatedUser(role = Role.ORG_ADMIN)
    void admin_patchWithoutVersion_returns400() throws Exception {
        mvc.perform(patch("/api/v1/users/{id}", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("version"));
    }

    @Test
    @WithAuthenticatedUser(role = Role.ORG_ADMIN)
    void lastAdmin_returns409() throws Exception {
        when(userService.update(any(), any(), any())).thenThrow(new ApiException(ErrorCode.LAST_ADMIN, "keep one"));
        mvc.perform(patch("/api/v1/users/{id}", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"AGENT\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN"));
    }

    @Test
    @WithAuthenticatedUser(role = Role.AGENT)
    void organization_getAllowed_patchForbiddenForAgent() throws Exception {
        when(organizationService.get(any())).thenReturn(new OrganizationResponse(UUID.randomUUID(), "Acme", "acme", Instant.now()));
        mvc.perform(get("/api/v1/organization")).andExpect(status().isOk()).andExpect(jsonPath("$.slug").value("acme"));
        mvc.perform(patch("/api/v1/organization").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hacked\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAuthenticatedUser(role = Role.ORG_ADMIN)
    void organization_patchByAdmin_ok() throws Exception {
        when(organizationService.rename(any(), any())).thenReturn(new OrganizationResponse(UUID.randomUUID(), "Acme Inc", "acme", Instant.now()));
        mvc.perform(patch("/api/v1/organization").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Acme Inc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Inc"));
    }
}
