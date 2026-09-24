package com.infinevo.core.employee.detail;

import com.infinevo.core.employee.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.util.UUID;

/**
 * The columns all five employee detail sections share (W-13.2, spec section 6).
 *
 * <p>A {@code @MappedSuperclass} and not an {@code @Entity}: there is no {@code core.employee_detail}
 * table and there must not be one. The five sections are five tables —
 * {@code V015__employee_personal.sql} through {@code V019__employee_bank.sql} — each with its own
 * unique index on {@code (tenant_id, employee_id)} and its own {@code tenant_isolation} policy. This
 * class carries the identity, the tenant, the employee link and the audit stamping so that the same
 * seven columns are not declared five times and then drift apart. Same reasoning as
 * {@code com.infinevo.core.org.OrgMaster}.
 *
 * <p><strong>The employee link is a {@code @ManyToOne} over a single column, deliberately.</strong>
 * It is modelled as many-to-one rather than one-to-one because the "one" half is enforced by the
 * unique index in each migration, not by the mapping: a {@code @OneToOne} would add nothing the
 * index does not already guarantee and would tempt an inverse side onto {@link Employee}, which
 * would make loading an employee load five more tables. One column also matters to the audit trail —
 * a property that maps to exactly one column is named by {@code AuditWriter.Column.of}, while a
 * multi-column property is redacted outright ({@code AuditEventListener.columnNames}).
 *
 * <p>A foreign key alone does <strong>not</strong> stop a detail row pointing at an employee in
 * another tenant: {@code migration_user} owns the tables and PostgreSQL runs referential-integrity
 * checks as the owner, bypassing row-level security. {@link AbstractEmployeeDetailServiceImpl} is
 * what refuses it, and {@code EmployeeDetailCascadeIT} is what proves it — spec section 7.
 */
@MappedSuperclass
public abstract class EmployeeDetail {

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

    /**
     * The employee this section belongs to. Not updatable: a section cannot be moved from one person
     * to another, which is the only thing a writable column here could ever be used for.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    protected EmployeeDetail() {}

    protected EmployeeDetail(UUID tenantId, Employee employee, String actor) {
        this.tenantId = tenantId;
        this.employee = employee;
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

    public Employee getEmployee() {
        return employee;
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
     * Records who last wrote this section, and when.
     *
     * <p>Called at the end of every subclass {@code apply(...)}. {@code @PreUpdate} stamps
     * {@code updated_at} as well, but only when Hibernate finds the row dirty — a write that changes
     * nothing else should still say who touched it.
     */
    void stamp(String actor) {
        this.updatedBy = actor;
        this.updatedAt = Instant.now();
    }
}
