package com.helpdesk.user;

import com.helpdesk.common.persistence.TenantOwnedEntity;
import com.helpdesk.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.OptimisticLock;
import org.springframework.data.annotation.LastModifiedDate;

/**
 * A person in exactly one organization with exactly one role. Never hard-deleted — deactivated
 * instead (FR-3). Emails are stored lowercase and are unique per organization (BR-9).
 */
@Entity
@Table(name = "users")
public class User extends TenantOwnedEntity {

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    // Logging in must not bump the optimistic-lock version (an admin editing the user would get a 409).
    @OptimisticLock(excluded = true)
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public User(UUID organizationId, String email, String passwordHash, String fullName, Role role) {
        super(organizationId);
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    public boolean isAssignable() {
        return active && role.isStaff();
    }

    public boolean isActiveCustomer() {
        return active && role == Role.CUSTOMER;
    }

    public boolean isActiveAdmin() {
        return active && role == Role.ORG_ADMIN;
    }

    public void rename(String newFullName) {
        this.fullName = newFullName;
    }

    public void changeRole(Role newRole) {
        this.role = newRole;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void recordLogin(Instant at) {
        this.lastLoginAt = at;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
