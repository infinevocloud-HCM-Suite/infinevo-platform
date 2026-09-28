package com.infinevo.payroll.fbp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Employee Flexible Benefit Plan component declaration line entity (W-27.2).
 * Sits as an overlay on a specific CTC structure version (payroll.ctc_structure).
 */
@Entity
@Table(
        name = "employee_fbp_component",
        schema = "payroll",
        indexes = {@Index(name = "idx_employee_fbp_component_tenant_employee", columnList = "tenant_id, employee_id")})
public class EmployeeFbpComponent {

    public static final String ACTOR_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "ctc_structure_id", nullable = false, updatable = false)
    private UUID ctcStructureId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "earning_id")
    private UUID earningId;

    @Column(name = "reimbursement_id")
    private UUID reimbursementId;

    @Column(name = "annual_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal annualAmount;

    @Column(name = "monthly_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal monthlyAmount;

    @Column(name = "declared_at", nullable = false)
    private Instant declaredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "declared_by", nullable = false, length = 16)
    private DeclaredBy declaredBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    protected EmployeeFbpComponent() {}

    public EmployeeFbpComponent(
            UUID tenantId,
            UUID ctcStructureId,
            UUID employeeId,
            UUID earningId,
            UUID reimbursementId,
            BigDecimal annualAmount,
            BigDecimal monthlyAmount,
            Instant declaredAt,
            DeclaredBy declaredBy,
            String actor) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.ctcStructureId = Objects.requireNonNull(ctcStructureId, "ctcStructureId must not be null");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId must not be null");
        if ((earningId == null) == (reimbursementId == null)) {
            throw new IllegalArgumentException("Exactly one of earningId or reimbursementId must be non-null");
        }
        this.earningId = earningId;
        this.reimbursementId = reimbursementId;
        this.annualAmount = Objects.requireNonNull(annualAmount, "annualAmount must not be null");
        this.monthlyAmount = Objects.requireNonNull(monthlyAmount, "monthlyAmount must not be null");
        this.declaredAt = Objects.requireNonNull(declaredAt, "declaredAt must not be null");
        this.declaredBy = Objects.requireNonNull(declaredBy, "declaredBy must not be null");
        this.createdBy = actor != null ? actor : ACTOR_SYSTEM;
        this.updatedBy = actor != null ? actor : ACTOR_SYSTEM;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (declaredAt == null) {
            declaredAt = now;
        }
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

    public UUID getCtcStructureId() {
        return ctcStructureId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public UUID getEarningId() {
        return earningId;
    }

    public void setEarningId(UUID earningId) {
        this.earningId = earningId;
    }

    public UUID getReimbursementId() {
        return reimbursementId;
    }

    public void setReimbursementId(UUID reimbursementId) {
        this.reimbursementId = reimbursementId;
    }

    public BigDecimal getAnnualAmount() {
        return annualAmount;
    }

    public void setAnnualAmount(BigDecimal annualAmount) {
        this.annualAmount = annualAmount;
    }

    public BigDecimal getMonthlyAmount() {
        return monthlyAmount;
    }

    public void setMonthlyAmount(BigDecimal monthlyAmount) {
        this.monthlyAmount = monthlyAmount;
    }

    public Instant getDeclaredAt() {
        return declaredAt;
    }

    public void setDeclaredAt(Instant declaredAt) {
        this.declaredAt = declaredAt;
    }

    public DeclaredBy getDeclaredBy() {
        return declaredBy;
    }

    public void setDeclaredBy(DeclaredBy declaredBy) {
        this.declaredBy = declaredBy;
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
