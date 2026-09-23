package com.infinevo.shared.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The local profile of one Keycloak user inside one tenant (W-10).
 *
 * <p>Maps {@code core.user_account} —
 * {@code migration/src/main/resources/db/migration/core/V009__user_account.sql}.
 *
 * <p><strong>One row per (user, tenant), not per user</strong> — spec section 13, decision 1.
 * Hard rule 7 admits no table outside {@code reference} without a {@code tenant_id}, so a user who
 * belongs to two tenants has two rows, each with its own copy of the name and email. The unique
 * index is {@code (tenant_id, keycloak_user_id)} ({@code V009:25}), which is what makes the
 * "one lookup, one row" contract of {@link UserAccountRepository} true.
 *
 * <p>No password column, and there must never be one: Keycloak is the sole credential authority
 * ({@code 01-platform-shape.md:63}). The legacy {@code OurUsers.password} and
 * {@code CompanyUser.password} columns this table merges are dropped deliberately —
 * {@code legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/OurUsers.java:29}
 * and {@code legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/CompanyUser.java:37}, as is
 * {@code OurUsers.blacklistedToken} ({@code OurUsers.java:44}) — a stateless token validated against JWKS has
 * nothing to blacklist. See the note at {@code V009:20-22}.
 *
 * <p>The columns that did carry over come from the same two rows of the frozen system:
 * {@code CompanyUser.userId} is the Keycloak user id ({@code CompanyUser.java:13}), and
 * {@code firstName} / {@code lastName} / {@code phoneNumber} are {@code CompanyUser.java:22-28}.
 *
 * <p>The claims are Keycloak's, not ours. {@link UserProfileSyncService} copies them in on the
 * first request after a login and refreshes them when they change; nothing else writes them, so
 * the row is a cache of the identity provider rather than a second source of truth.
 */
@Entity
@Table(
        name = "user_account",
        schema = "core",
        indexes = {
            @Index(name = "idx_user_account_tenant_keycloak_user", columnList = "tenant_id, keycloak_user_id"),
            @Index(name = "idx_user_account_tenant_email", columnList = "tenant_id, email")
        })
public class UserAccount {

    /** The status of a profile synced from a token. Keycloak decides who may log in at all. */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** Written into {@code created_by} / {@code updated_by}: nobody typed this row in. */
    public static final String ACTOR_TOKEN_SYNC = "token-sync";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "keycloak_user_id", nullable = false, updatable = false)
    private UUID keycloakUserId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_ACTIVE;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_TOKEN_SYNC;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_TOKEN_SYNC;

    protected UserAccount() {}

    UserAccount(UUID tenantId, UUID keycloakUserId, String email, String firstName, String lastName, Instant now) {
        this.tenantId = tenantId;
        this.keycloakUserId = keycloakUserId;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = STATUS_ACTIVE;
        this.lastSyncedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getKeycloakUserId() {
        return keycloakUserId;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getStatus() {
        return status;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    /**
     * Copies the three synced claims in and stamps the row.
     *
     * <p>Package-private, and called only by {@link UserProfileSyncService} once it has decided
     * something actually changed. Calling it unconditionally would make Hibernate's dirty check
     * fire on every request and {@code updated_at} churn — spec section 7.
     */
    void applyClaims(String email, String firstName, String lastName, Instant now) {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.lastSyncedAt = now;
        this.updatedAt = now;
        this.updatedBy = ACTOR_TOKEN_SYNC;
    }

    /** True when the token says something this row does not. */
    boolean differsFrom(String email, String firstName, String lastName) {
        return !Objects.equals(this.email, email)
                || !Objects.equals(this.firstName, firstName)
                || !Objects.equals(this.lastName, lastName);
    }
}
