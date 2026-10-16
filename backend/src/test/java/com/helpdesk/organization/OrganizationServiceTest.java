package com.helpdesk.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.helpdesk.common.error.ApiException;
import com.helpdesk.common.error.ErrorCode;
import com.helpdesk.security.AuthenticatedUser;
import com.helpdesk.security.Role;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    OrganizationRepository organizations;
    @InjectMocks
    OrganizationService service;

    final Organization acme = new Organization("Acme", "acme");

    @Test
    void register_duplicateSlug_throws409() {
        when(organizations.existsBySlug("acme")).thenReturn(true);
        assertThatThrownBy(() -> service.register("Acme", "acme"))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.SLUG_ALREADY_EXISTS);
        verify(organizations, never()).saveAndFlush(any());
    }

    @Test
    void register_saves() {
        when(organizations.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        Organization org = service.register("Globex", "globex");
        assertThat(org.getSlug()).isEqualTo("globex");
    }

    @Test
    void get_and_rename_useActorsOrgOnly() {
        AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), acme.getId(), Role.ORG_ADMIN, "a@acme.test");
        when(organizations.findById(acme.getId())).thenReturn(Optional.of(acme));
        assertThat(service.get(admin).name()).isEqualTo("Acme");
        assertThat(service.rename(admin, "Acme Inc").name()).isEqualTo("Acme Inc");
        assertThat(service.rename(admin, "Acme Inc").slug()).isEqualTo("acme");
        assertThat(service.getRef(acme.getId()).slug()).isEqualTo("acme");
    }

    @Test
    void rename_byAgent_throws403() {
        AuthenticatedUser agent = new AuthenticatedUser(UUID.randomUUID(), acme.getId(), Role.AGENT, "b@acme.test");
        assertThatThrownBy(() -> service.rename(agent, "Hacked"))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.ACCESS_DENIED);
    }
}
