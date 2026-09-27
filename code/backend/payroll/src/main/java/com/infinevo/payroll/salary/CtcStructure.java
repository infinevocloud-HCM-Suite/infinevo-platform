package com.infinevo.payroll.salary;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dated CTC structure version entity for an employee (W-26.2).
 */
@Entity
@Table(
        name = "ctc_structure",
        schema = "payroll",
        indexes = {
            @Index(
                    name = "uk_ctc_structure_tenant_employee_effective",
                    columnList = "tenant_id, employee_id, effective_from",
                    unique = true),
            @Index(
                    name = "idx_ctc_structure_tenant_employee_cancelled",
                    columnList = "tenant_id, employee_id, is_cancelled, effective_from DESC")
        })
public class CtcStructure {

    public static final String ACTOR_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "annual_ctc", nullable = false, precision = 19, scale = 4)
    private BigDecimal annualCtc;

    @Column(name = "monthly_ctc", nullable = false, precision = 19, scale = 4)
    private BigDecimal monthlyCtc;

    @Column(name = "is_cancelled", nullable = false)
    private boolean cancelled = false;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    @OneToMany(mappedBy = "ctcStructure", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmployeeEarning> earnings = new ArrayList<>();

    @OneToMany(mappedBy = "ctcStructure", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmployeeBenefit> benefits = new ArrayList<>();

    @OneToMany(mappedBy = "ctcStructure", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EmployeeReimbursement> reimbursements = new ArrayList<>();

    protected CtcStructure() {}

    public CtcStructure(UUID tenantId, UUID employeeId, LocalDate effectiveFrom, String actor) {
        this.tenantId = tenantId;
        this.employeeId = employeeId;
        this.effectiveFrom = effectiveFrom;
        this.createdBy = actor != null ? actor : ACTOR_SYSTEM;
        this.updatedBy = actor != null ? actor : ACTOR_SYSTEM;
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

    public UUID getEmployeeId() {
        return employeeId;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public BigDecimal getAnnualCtc() {
        return annualCtc;
    }

    public void setAnnualCtc(BigDecimal annualCtc) {
        this.annualCtc = annualCtc;
    }

    public BigDecimal getMonthlyCtc() {
        return monthlyCtc;
    }

    public void setMonthlyCtc(BigDecimal monthlyCtc) {
        this.monthlyCtc = monthlyCtc;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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

    public List<EmployeeEarning> getEarnings() {
        return earnings;
    }

    public void setEarnings(List<EmployeeEarning> earnings) {
        this.earnings = earnings;
    }

    public List<EmployeeBenefit> getBenefits() {
        return benefits;
    }

    public void setBenefits(List<EmployeeBenefit> benefits) {
        this.benefits = benefits;
    }

    public List<EmployeeReimbursement> getReimbursements() {
        return reimbursements;
    }

    public void setReimbursements(List<EmployeeReimbursement> reimbursements) {
        this.reimbursements = reimbursements;
    }
}
