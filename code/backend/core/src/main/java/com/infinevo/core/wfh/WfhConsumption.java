package com.infinevo.core.wfh;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only immutable WFH quota consumption ledger (W-74).
 * Records every deduction on approval, and reversal on cancellation.
 */
@Entity
@Table(
        name = "wfh_consumption",
        schema = "core",
        indexes = {
            @Index(name = "idx_wfh_consumption_tenant_emp_period", columnList = "tenant_id, employee_id, period"),
            @Index(name = "idx_wfh_consumption_tenant_allocation", columnList = "tenant_id, allocation_id"),
            @Index(name = "idx_wfh_consumption_tenant_request", columnList = "tenant_id, wfh_request_id"),
            @Index(name = "idx_wfh_consumption_tenant_reverses", columnList = "tenant_id, reverses_id")
        })
public class WfhConsumption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "allocation_id", nullable = false, updatable = false)
    private UUID allocationId;

    @Column(name = "wfh_request_id", updatable = false)
    private UUID wfhRequestId;

    @Column(name = "consumed_days", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal consumedDays;

    @Column(name = "consumed_on", nullable = false, updatable = false)
    private LocalDate consumedOn;

    @Column(name = "period", nullable = false, length = 7, updatable = false)
    private String period; // YYYY-MM

    @Column(name = "reverses_id", updatable = false)
    private UUID reversesId;

    @Column(name = "reason", length = 500, updatable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, updatable = false, length = 100)
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

    public UUID getAllocationId() {
        return allocationId;
    }

    public void setAllocationId(UUID allocationId) {
        this.allocationId = allocationId;
    }

    public UUID getWfhRequestId() {
        return wfhRequestId;
    }

    public void setWfhRequestId(UUID wfhRequestId) {
        this.wfhRequestId = wfhRequestId;
    }

    public BigDecimal getConsumedDays() {
        return consumedDays;
    }

    public void setConsumedDays(BigDecimal consumedDays) {
        this.consumedDays = consumedDays;
    }

    public LocalDate getConsumedOn() {
        return consumedOn;
    }

    public void setConsumedOn(LocalDate consumedOn) {
        this.consumedOn = consumedOn;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public UUID getReversesId() {
        return reversesId;
    }

    public void setReversesId(UUID reversesId) {
        this.reversesId = reversesId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
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
        WfhConsumption that = (WfhConsumption) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
