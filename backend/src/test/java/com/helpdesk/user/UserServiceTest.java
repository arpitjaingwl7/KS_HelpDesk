package com.helpdesk.user;

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
import com.helpdesk.support.Fixtures;
import com.helpdesk.user.dto.CreateUserRequest;
import com.helpdesk.user.dto.UpdateUserRequest;
import com.helpdesk.user.dto.UserDetail;
import com.helpdesk.user.dto.UserRef;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository users;

    final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    UserService service;

    final UUID orgId = UUID.randomUUID();
    final User admin = Fixtures.user(orgId, Role.ORG_ADMIN);
    final AuthenticatedUser adminActor = Fixtures.actor(admin);

    @BeforeEach
    void setUp() {
        service = new UserService(users, encoder, new UserMapperImpl(), Clock.fixed(Fixtures.NOW, ZoneOffset.UTC));
    }

    @Test
    void create_storesLowercaseEmail_andHashedPassword() {
        when(users.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        UserDetail created = service.create(adminActor, new CreateUserRequest("Bob", "Bob@Acme.COM", "Initial#123", Role.AGENT));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("bob@acme.com");
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("Initial#123");
        assertThat(encoder.matches("Initial#123", saved.getValue().getPasswordHash())).isTrue();
        assertThat(saved.getValue().getOrganizationId()).isEqualTo(orgId);
        assertThat(created.email()).isEqualTo("bob@acme.com");
    }

    @Test
    void create_duplicateEmailCaseInsensitive_throws409() {
        when(users.existsByOrganizationIdAndEmail(orgId, "bob@acme.com")).thenReturn(true);
        assertCode(() -> service.create(adminActor, new CreateUserRequest("Bob", "BOB@acme.com", "Initial#123", Role.AGENT)),
                ErrorCode.EMAIL_ALREADY_EXISTS);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void nonAdmin_cannotManageUsers() {
        AuthenticatedUser agent = Fixtures.actor(Fixtures.user(orgId, Role.AGENT));
        assertCode(() -> service.create(agent, new CreateUserRequest("Bob", "b@a.com", "Initial#123", Role.AGENT)), ErrorCode.ACCESS_DENIED);
        assertCode(() -> service.get(agent, UUID.randomUUID()), ErrorCode.ACCESS_DENIED);
    }

    @Test
    void get_userOfOtherOrg_throws404() {
        UUID foreign = UUID.randomUUID();
        when(users.findByIdAndOrganizationId(foreign, orgId)).thenReturn(Optional.empty());
        assertCode(() -> service.get(adminActor, foreign), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void update_demotingLastAdmin_throwsLastAdmin() {
        User otherAdmin = Fixtures.user(orgId, Role.ORG_ADMIN);
        when(users.findByIdAndOrganizationId(otherAdmin.getId(), orgId)).thenReturn(Optional.of(otherAdmin));
        when(users.countByOrganizationIdAndRoleAndActiveTrue(orgId, Role.ORG_ADMIN)).thenReturn(1L);
        assertCode(() -> service.update(adminActor, otherAdmin.getId(), new UpdateUserRequest(null, Role.AGENT, null, 0L)),
                ErrorCode.LAST_ADMIN);
    }

    @Test
    void update_deactivatingLastAdmin_throwsLastAdmin() {
        User otherAdmin = Fixtures.user(orgId, Role.ORG_ADMIN);
        when(users.findByIdAndOrganizationId(otherAdmin.getId(), orgId)).thenReturn(Optional.of(otherAdmin));
        when(users.countByOrganizationIdAndRoleAndActiveTrue(orgId, Role.ORG_ADMIN)).thenReturn(1L);
        assertCode(() -> service.update(adminActor, otherAdmin.getId(), new UpdateUserRequest(null, null, false, 0L)),
                ErrorCode.LAST_ADMIN);
    }

    @Test
    void update_demotingAdminWhenAnotherExists_succeeds() {
        User otherAdmin = Fixtures.user(orgId, Role.ORG_ADMIN);
        when(users.findByIdAndOrganizationId(otherAdmin.getId(), orgId)).thenReturn(Optional.of(otherAdmin));
        when(users.countByOrganizationIdAndRoleAndActiveTrue(orgId, Role.ORG_ADMIN)).thenReturn(2L);
        UserDetail updated = service.update(adminActor, otherAdmin.getId(), new UpdateUserRequest("Renamed", Role.AGENT, null, 0L));
        assertThat(updated.role()).isEqualTo(Role.AGENT);
        assertThat(updated.fullName()).isEqualTo("Renamed");
    }

    @Test
    void update_selfDeactivation_throws() {
        when(users.findByIdAndOrganizationId(admin.getId(), orgId)).thenReturn(Optional.of(admin));
        assertCode(() -> service.update(adminActor, admin.getId(), new UpdateUserRequest(null, null, false, 0L)),
                ErrorCode.SELF_DEACTIVATION);
    }

    @Test
    void update_staleVersion_throwsConcurrentModification() {
        User agent = Fixtures.user(orgId, Role.AGENT);
        when(users.findByIdAndOrganizationId(agent.getId(), orgId)).thenReturn(Optional.of(agent));
        assertCode(() -> service.update(adminActor, agent.getId(), new UpdateUserRequest("x", null, null, 5L)),
                ErrorCode.CONCURRENT_MODIFICATION);
    }

    @Test
    void update_deactivateAgent_succeeds() {
        User agent = Fixtures.user(orgId, Role.AGENT);
        when(users.findByIdAndOrganizationId(agent.getId(), orgId)).thenReturn(Optional.of(agent));
        assertThat(service.update(adminActor, agent.getId(), new UpdateUserRequest(null, null, false, 0L)).active()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_and_pickers_areOrgScoped() {
        when(users.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(admin)));
        assertThat(service.list(adminActor, "ali", Role.ORG_ADMIN, true, PageRequest.of(0, 20)).content()).hasSize(1);
        assertThat(service.assignableAgents(adminActor, null)).hasSize(1);
        assertThat(service.activeCustomers(adminActor, "x")).hasSize(1);
    }

    @Test
    void pickers_customer_throws403() {
        AuthenticatedUser customer = Fixtures.actor(Fixtures.user(orgId, Role.CUSTOMER));
        assertCode(() -> service.assignableAgents(customer, null), ErrorCode.ACCESS_DENIED);
    }

    @Test
    void findAssignable_onlyActiveStaff() {
        User agent = Fixtures.user(orgId, Role.AGENT);
        User customer = Fixtures.user(orgId, Role.CUSTOMER);
        User inactive = Fixtures.inactive(Fixtures.user(orgId, Role.AGENT));
        when(users.findByIdAndOrganizationId(agent.getId(), orgId)).thenReturn(Optional.of(agent));
        when(users.findByIdAndOrganizationId(customer.getId(), orgId)).thenReturn(Optional.of(customer));
        when(users.findByIdAndOrganizationId(inactive.getId(), orgId)).thenReturn(Optional.of(inactive));
        assertThat(service.findAssignable(orgId, agent.getId())).contains(agent);
        assertThat(service.findAssignable(orgId, customer.getId())).isEmpty();
        assertThat(service.findAssignable(orgId, inactive.getId())).isEmpty();
        assertThat(service.findActiveCustomer(orgId, customer.getId())).contains(customer);
        assertThat(service.findActiveCustomer(orgId, agent.getId())).isEmpty();
    }

    @Test
    void refsById_batchesLookup() {
        User agent = Fixtures.user(orgId, Role.AGENT);
        when(users.findByOrganizationIdAndIdIn(orgId, Set.of(agent.getId()))).thenReturn(List.of(agent));
        Map<UUID, UserRef> refs = service.refsById(orgId, Set.of(agent.getId()));
        assertThat(refs).containsEntry(agent.getId(), new UserRef(agent.getId(), agent.getFullName()));
        assertThat(service.refsById(orgId, Set.of())).isEmpty();
    }

    @Test
    void recordLogin_setsLastLoginAt() {
        when(users.findByIdAndOrganizationId(admin.getId(), orgId)).thenReturn(Optional.of(admin));
        service.recordLogin(admin);
        assertThat(admin.getLastLoginAt()).isEqualTo(Fixtures.NOW);
    }

    private static void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo(code);
    }
}
