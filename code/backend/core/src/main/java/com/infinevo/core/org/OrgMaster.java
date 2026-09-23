package com.infinevo.core.org;

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
 * The columns {@link Department}, {@link Designation} and {@link WorkLocation} share (W-14.1, spec
 * section 4).
 *
 * <p>A {@code @MappedSuperclass} and not an {@code @Entity}: there is no {@code core.org_master}
 * table and there must not be one. The three masters are three tables — {@code V011__department.sql},
 * {@code V012__designation.sql}, {@code V013__work_location.sql} — each with its own unique index on
 * {@code (tenant_id, code)} and its own {@code tenant_isolation} policy. This class carries the
 * column shape and the audit stamping so that the same seven columns are not declared three times
 * and then drift apart; it carries no table and no identity of its own.
 *
 * <p>Two of the three are genuinely identical. {@link WorkLocation} adds the address and the
 * filing-address flag, which is exactly the shape Payroll already models
 * ({@code legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/organization/WorkLocation.java}).
 *
 * <p>Deliberately absent, and to stay absent: a {@code parent_id} on a department (founder decision
 * 2, spec section 13 — departments are flat) and a level or grade on a designation (decision 1 — pay
 * grade lives on the employment record). Adding either is a later ticket, not an economy taken here.
 */
@MappedSuperclass
public abstract class OrgMaster {

    /** Written into {@code created_by} / {@code updated_by} when no authenticated user is on the thread. */
    public static final String ACTOR_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Never taken from the request. The service reads it from {@code TenantContext}, which the
     * binding filter set from the verified token — spec section 3.
     */
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /**
     * Payroll's status flag, kept on purpose (spec section 4). Deactivating hides a value from new
     * assignments without breaking the employees already holding it, which is the supported
     * alternative to a deletion that is refused while anyone points at the row.
     */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    protected OrgMaster() {}

    protected OrgMaster(UUID tenantId, String actor) {
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

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
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
     * Copies the mutable common fields in and stamps the row.
     *
     * <p>Package-private, and called only by {@link AbstractOrgMasterServiceImpl} once it has
     * validated the request. Neither {@code id} nor {@code tenantId} is reachable from here: a row
     * cannot change tenant, which is the whole point of reading the tenant from the context rather
     * than from the request.
     */
    void apply(String code, String name, boolean active, String actor) {
        this.code = code;
        this.name = name;
        this.active = active;
        this.updatedBy = actor;
        this.updatedAt = Instant.now();
    }
}
