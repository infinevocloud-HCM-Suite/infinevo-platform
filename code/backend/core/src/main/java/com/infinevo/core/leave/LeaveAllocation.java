package com.infinevo.core.leave;

import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entity representing an employee's leave allocation for a specific leave type and year (W-16.2).
 */
@Entity
@Audited
@Table(
        name = "leave_allocation",
        schema = "core",
        indexes = {
            @Index(name = "idx_leave_allocation_accrual_sweep", columnList = "tenant_id, last_accrued_on"),
            @Index(
                    name = "idx_leave_allocation_lookup",
                    columnList = "tenant_id, employee_id, year_start_date, year_end_date")
        })
public class LeaveAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "leave_type_id", nullable = false)
    private UUID leaveTypeId;

    @Column(name = "leave_year", nullable = false, length = 9)
    private String leaveYear;

    @Column(name = "year_start_date", nullable = false)
    private LocalDate yearStartDate;

    @Column(name = "year_end_date", nullable = false)
    private LocalDate yearEndDate;

    @Column(name = "entitlement_days", nullable = false, precision = 10, scale = 2)
    private BigDecimal entitlementDays;

    @Column(name = "accrued_days", nullable = false, precision = 10, scale = 2)
    private BigDecimal accruedDays;

    @Column(name = "carried_forward_days", nullable = false, precision = 10, scale = 2)
    private BigDecimal carriedForwardDays;

    @Column(name = "carry_forward_expires_on")
    private LocalDate carryForwardExpiresOn;

    @Column(name = "pro_rate_factor", nullable = false, precision = 5, scale = 4)
    private BigDecimal proRateFactor;

    @Column(name = "last_accrued_on")
    private LocalDate lastAccruedOn;

    @Column(name = "last_reset_on")
    private LocalDate lastResetOn;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    public LeaveAllocation() {}

    public LeaveAllocation(
            UUID tenantId,
            UUID employeeId,
            UUID leaveTypeId,
            String leaveYear,
            LocalDate yearStartDate,
            LocalDate yearEndDate,
            BigDecimal entitlementDays,
            BigDecimal accruedDays,
            BigDecimal carriedForwardDays,
            LocalDate carryForwardExpiresOn,
            BigDecimal proRateFactor,
            UUID policyId) {
        this.tenantId = tenantId;
        this.employeeId = employeeId;
        this.leaveTypeId = leaveTypeId;
        this.leaveYear = leaveYear;
        this.yearStartDate = yearStartDate;
        this.yearEndDate = yearEndDate;
        this.entitlementDays = entitlementDays;
        this.accruedDays = accruedDays != null ? accruedDays : BigDecimal.ZERO;
        this.carriedForwardDays = carriedForwardDays != null ? carriedForwardDays : BigDecimal.ZERO;
        this.carryForwardExpiresOn = carryForwardExpiresOn;
        this.proRateFactor = proRateFactor != null ? proRateFactor : BigDecimal.ONE;
        this.policyId = policyId;
    }

    @PrePersist
    public void onPrePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (createdBy == null) {
            createdBy = "system";
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (updatedBy == null) {
            updatedBy = "system";
        }
        if (accruedDays == null) {
            accruedDays = BigDecimal.ZERO;
        }
        if (carriedForwardDays == null) {
            carriedForwardDays = BigDecimal.ZERO;
        }
        if (proRateFactor == null) {
            proRateFactor = BigDecimal.ONE;
        }
    }

    @PreUpdate
    public void onPreUpdate() {
        updatedAt = Instant.now();
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

    public String getLeaveYear() {
        return leaveYear;
    }

    public void setLeaveYear(String leaveYear) {
        this.leaveYear = leaveYear;
    }

    public LocalDate getYearStartDate() {
        return yearStartDate;
    }

    public void setYearStartDate(LocalDate yearStartDate) {
        this.yearStartDate = yearStartDate;
    }

    public LocalDate getYearEndDate() {
        return yearEndDate;
    }

    public void setYearEndDate(LocalDate yearEndDate) {
        this.yearEndDate = yearEndDate;
    }

    public BigDecimal getEntitlementDays() {
        return entitlementDays;
    }

    public void setEntitlementDays(BigDecimal entitlementDays) {
        this.entitlementDays = entitlementDays;
    }

    public BigDecimal getAccruedDays() {
        return accruedDays;
    }

    public void setAccruedDays(BigDecimal accruedDays) {
        this.accruedDays = accruedDays;
    }

    public BigDecimal getCarriedForwardDays() {
        return carriedForwardDays;
    }

    public void setCarriedForwardDays(BigDecimal carriedForwardDays) {
        this.carriedForwardDays = carriedForwardDays;
    }

    public LocalDate getCarryForwardExpiresOn() {
        return carryForwardExpiresOn;
    }

    public void setCarryForwardExpiresOn(LocalDate carryForwardExpiresOn) {
        this.carryForwardExpiresOn = carryForwardExpiresOn;
    }

    public BigDecimal getProRateFactor() {
        return proRateFactor;
    }

    public void setProRateFactor(BigDecimal proRateFactor) {
        this.proRateFactor = proRateFactor;
    }

    public LocalDate getLastAccruedOn() {
        return lastAccruedOn;
    }

    public void setLastAccruedOn(LocalDate lastAccruedOn) {
        this.lastAccruedOn = lastAccruedOn;
    }

    public LocalDate getLastResetOn() {
        return lastResetOn;
    }

    public void setLastResetOn(LocalDate lastResetOn) {
        this.lastResetOn = lastResetOn;
    }

    public UUID getPolicyId() {
        return policyId;
    }

    public void setPolicyId(UUID policyId) {
        this.policyId = policyId;
    }

    public Instant getCreatedAt() {
        return createdAt;
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

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
