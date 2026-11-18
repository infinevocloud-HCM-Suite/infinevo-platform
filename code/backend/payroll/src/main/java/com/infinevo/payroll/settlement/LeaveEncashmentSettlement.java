package com.infinevo.payroll.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing an employee's leave encashment settlement on exit (W-90.1).
 * Maps to {@code payroll.leave_encashment_settlement}.
 */
@Entity
@Table(name = "leave_encashment_settlement", schema = "payroll")
public class LeaveEncashmentSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "leave_type_id", nullable = false)
    private UUID leaveTypeId;

    @Column(name = "encashable_days", nullable = false, precision = 6, scale = 2)
    private BigDecimal encashableDays;

    @Column(name = "per_day_rate", nullable = false, precision = 14, scale = 2)
    private BigDecimal perDayRate;

    @Column(name = "gross_encashment_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossEncashmentAmount;

    @Column(name = "tax_exempt_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxExemptAmount;

    @Column(name = "taxable_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxableAmount;

    @Column(name = "statutory_ceiling", nullable = false, precision = 14, scale = 2)
    private BigDecimal statutoryCeiling;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    public LeaveEncashmentSettlement() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.createdBy = "system";
        this.updatedBy = "system";
        this.status = "DRAFT";
        this.statutoryCeiling = BigDecimal.valueOf(2500000.00);
    }

    public LeaveEncashmentSettlement(
            UUID tenantId,
            UUID employeeId,
            UUID leaveTypeId,
            BigDecimal encashableDays,
            BigDecimal perDayRate,
            BigDecimal grossEncashmentAmount,
            BigDecimal taxExemptAmount,
            BigDecimal taxableAmount,
            BigDecimal statutoryCeiling,
            String status,
            String createdBy) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId");
        this.leaveTypeId = Objects.requireNonNull(leaveTypeId, "leaveTypeId");
        this.encashableDays = Objects.requireNonNull(encashableDays, "encashableDays");
        this.perDayRate = Objects.requireNonNull(perDayRate, "perDayRate");
        this.grossEncashmentAmount = Objects.requireNonNull(grossEncashmentAmount, "grossEncashmentAmount");
        this.taxExemptAmount = Objects.requireNonNull(taxExemptAmount, "taxExemptAmount");
        this.taxableAmount = Objects.requireNonNull(taxableAmount, "taxableAmount");
        this.statutoryCeiling = statutoryCeiling != null ? statutoryCeiling : BigDecimal.valueOf(2500000.00);
        this.status = status != null ? status : "DRAFT";
        this.createdBy = createdBy != null ? createdBy : "system";
        this.updatedBy = this.createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public UUID getLeaveTypeId() {
        return leaveTypeId;
    }

    public void setLeaveTypeId(UUID leaveTypeId) {
        this.leaveTypeId = leaveTypeId;
    }

    public BigDecimal getEncashableDays() {
        return encashableDays;
    }

    public void setEncashableDays(BigDecimal encashableDays) {
        this.encashableDays = encashableDays;
    }

    public BigDecimal getPerDayRate() {
        return perDayRate;
    }

    public void setPerDayRate(BigDecimal perDayRate) {
        this.perDayRate = perDayRate;
    }

    public BigDecimal getGrossEncashmentAmount() {
        return grossEncashmentAmount;
    }

    public void setGrossEncashmentAmount(BigDecimal grossEncashmentAmount) {
        this.grossEncashmentAmount = grossEncashmentAmount;
    }

    public BigDecimal getTaxExemptAmount() {
        return taxExemptAmount;
    }

    public void setTaxExemptAmount(BigDecimal taxExemptAmount) {
        this.taxExemptAmount = taxExemptAmount;
    }

    public BigDecimal getTaxableAmount() {
        return taxableAmount;
    }

    public void setTaxableAmount(BigDecimal taxableAmount) {
        this.taxableAmount = taxableAmount;
    }

    public BigDecimal getStatutoryCeiling() {
        return statutoryCeiling;
    }

    public void setStatutoryCeiling(BigDecimal statutoryCeiling) {
        this.statutoryCeiling = statutoryCeiling;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
