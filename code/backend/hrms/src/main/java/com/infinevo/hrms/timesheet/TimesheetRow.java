package com.infinevo.hrms.timesheet;

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
 * What every timesheet table has in common: a UUID key, the tenant, and the four audit columns (W-42.1).
 *
 * <p>The tenant is set once, from {@code TenantContext}, by the service that builds the row; row-level security
 * is what keeps every other tenant's rows out of reach.
 *
 * <p>Public, though nothing outside the package extends it: a Hibernate proxy of a lazy parent (a project line's
 * timesheet) calls the inherited getters by reflection, which a package-private declaring class refuses.
 */
@MappedSuperclass
public abstract class TimesheetRow {

    static final String ACTOR_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

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

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (createdBy == null) {
            createdBy = ACTOR_SYSTEM;
        }
        if (updatedBy == null) {
            updatedBy = ACTOR_SYSTEM;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
        if (updatedBy == null) {
            updatedBy = ACTOR_SYSTEM;
        }
    }

    /** Stamps who made the row, for both the create and the update columns. */
    void stamp(UUID tenantId, String actor) {
        this.tenantId = tenantId;
        String who = actor != null ? actor : ACTOR_SYSTEM;
        this.createdBy = who;
        this.updatedBy = who;
    }

    /**
     * Marks the row as changed now, by {@code actor}, even when none of its own columns changed: a replace rewrites
     * the children and the header would otherwise keep its old update time.
     */
    void touch(String actor) {
        this.updatedBy = actor != null ? actor : ACTOR_SYSTEM;
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

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy != null ? updatedBy : ACTOR_SYSTEM;
    }
}
