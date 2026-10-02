package com.infinevo.core.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only leave consumption record (W-16.4a, spec section 4 & 6).
 */
@Entity
@Table(name = "leave_consumption", schema = "core")
public class LeaveConsumption {

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

    @Column(name = "leave_request_id", updatable = false)
    private UUID leaveRequestId;

    @Column(name = "consumed_days", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal consumedDays;

    @Column(name = "consumed_on", nullable = false, updatable = false)
    private LocalDate consumedOn;

    @Column(name = "period", nullable = false, length = 7, updatable = false)
    private String period;

    @Column(name = "reverses_id", updatable = false)
    private UUID reversesId;

    @Column(name = "reason", length = 500, updatable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    public LeaveConsumption() {}

    public LeaveConsumption(
            UUID tenantId,
            UUID employeeId,
            UUID allocationId,
            UUID leaveRequestId,
            BigDecimal consumedDays,
            LocalDate consumedOn,
            String period,
            UUID reversesId,
            String reason) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId must not be null");
        this.allocationId = Objects.requireNonNull(allocationId, "allocationId must not be null");
        this.leaveRequestId = leaveRequestId;
        this.consumedDays = Objects.requireNonNull(consumedDays, "consumedDays must not be null");
        this.consumedOn = Objects.requireNonNull(consumedOn, "consumedOn must not be null");
        this.period = Objects.requireNonNull(period, "period must not be null");
        this.reversesId = reversesId;
        this.reason = reason;
    }

    @PrePersist
    void onPrePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (createdBy == null) {
            createdBy = "system";
        }
        if (updatedBy == null) {
            updatedBy = "system";
        }
    }

    @PreUpdate
    void onPreUpdate() {
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

    public UUID getAllocationId() {
        return allocationId;
    }

    public void setAllocationId(UUID allocationId) {
        this.allocationId = allocationId;
    }

    public UUID getLeaveRequestId() {
        return leaveRequestId;
    }

    public void setLeaveRequestId(UUID leaveRequestId) {
        this.leaveRequestId = leaveRequestId;
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
}
