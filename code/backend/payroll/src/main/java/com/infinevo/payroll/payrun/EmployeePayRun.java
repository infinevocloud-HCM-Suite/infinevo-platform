package com.infinevo.payroll.payrun;

import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * One employee a run considered, {@code INCLUDED} or {@code SKIPPED} with the reason (W-29.1 §6).
 * No name or number snapshot (§13 decision 7) and no money column — W-29.2 adds those.
 */
@Entity
@Table(name = "employee_payrun", schema = "payroll")
@Audited
public class EmployeePayRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "payrun_id", nullable = false, updatable = false)
    private UUID payrunId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "salary_version_id", updatable = false)
    private UUID salaryVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "inclusion_status", nullable = false, length = 16, updatable = false)
    private InclusionStatus inclusionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "skip_reason", length = 32, updatable = false)
    private SkipReason skipReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    protected EmployeePayRun() {}

    public EmployeePayRun(UUID tenantId, UUID payrunId, InclusionDecision decision, String actor) {
        Objects.requireNonNull(decision, "decision must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.payrunId = Objects.requireNonNull(payrunId, "payrunId must not be null");
        this.employeeId = decision.employeeId();
        this.salaryVersionId = decision.salaryVersionId();
        this.inclusionStatus = decision.inclusionStatus();
        this.skipReason = decision.skipReason();
        this.createdBy = Objects.requireNonNull(actor, "actor must not be null");
        this.updatedBy = actor;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getPayrunId() {
        return payrunId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public UUID getSalaryVersionId() {
        return salaryVersionId;
    }

    public InclusionStatus getInclusionStatus() {
        return inclusionStatus;
    }

    public SkipReason getSkipReason() {
        return skipReason;
    }
}
