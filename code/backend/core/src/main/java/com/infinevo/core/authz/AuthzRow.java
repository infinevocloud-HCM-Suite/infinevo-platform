package com.infinevo.core.authz;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.util.UUID;

/**
 * The six columns {@link Role}, {@link RoleAction} and {@link UserRole} share (W-11.1): the key, the
 * tenant and the four audit columns of {@code V021}-{@code V023}.
 *
 * <p>A {@code @MappedSuperclass}, not an entity — three tables, each with its own
 * {@code tenant_isolation} policy. Same reasoning as {@code com.infinevo.core.org.OrgMaster}.
 */
@MappedSuperclass
public abstract class AuthzRow {

    /** Written into {@code created_by} / {@code updated_by} when no authenticated user is on the thread. */
    public static final String ACTOR_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Never taken from a request. The service reads it from {@code TenantContext} (W-08). */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    protected AuthzRow() {}

    protected AuthzRow(UUID tenantId, String actor) {
        this.tenantId = tenantId;
        this.createdBy = actor;
        this.updatedBy = actor;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Stamps a change. Called by the subclass's own mutator, never directly by a service. */
    protected void touch(String actor) {
        this.updatedBy = actor;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
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
}
