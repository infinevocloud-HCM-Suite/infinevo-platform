package com.infinevo.core.wfh;

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
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing an employee's annual WFH quota allocation (W-74).
 */
@Entity
@Audited
@Table(
        name = "wfh_allocation",
        schema = "core",
        indexes = {
            @Index(
                    name = "idx_wfh_allocation_lookup",
                    columnList = "tenant_id, employee_id, year_start_date, year_end_date")
        })
public class WfhAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "wfh_year", nullable = false, length = 9)
    private String wfhYear;

    @Column(name = "year_start_date", nullable = false)
    private LocalDate yearStartDate;

    @Column(name = "year_end_date", nullable = false)
    private LocalDate yearEndDate;

    @Column(name = "entitlement_days", nullable = false, precision = 10, scale = 2)
    private BigDecimal entitlementDays = new BigDecimal("12.00");

    @Column(name = "pro_rate_factor", nullable = false, precision = 5, scale = 4)
    private BigDecimal proRateFactor = new BigDecimal("1.0000");

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
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

    public UUID getPolicyId() {
        return policyId;
    }

    public void setPolicyId(UUID policyId) {
        this.policyId = policyId;
    }

    public String getWfhYear() {
        return wfhYear;
    }

    public void setWfhYear(String wfhYear) {
        this.wfhYear = wfhYear;
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

    public BigDecimal getProRateFactor() {
        return proRateFactor;
    }

    public void setProRateFactor(BigDecimal proRateFactor) {
        this.proRateFactor = proRateFactor;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WfhAllocation that = (WfhAllocation) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
