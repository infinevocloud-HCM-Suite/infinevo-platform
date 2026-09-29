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
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only monthly loss-of-pay delta record (W-16.4a, spec section 4 & 6).
 */
@Entity
@Table(name = "leave_monthly_lop", schema = "core")
public class LeaveMonthlyLop {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "period", nullable = false, length = 7, updatable = false)
    private String period;

    @Column(name = "leave_type_id", updatable = false)
    private UUID leaveTypeId;

    @Column(name = "leave_request_id", updatable = false)
    private UUID leaveRequestId;

    @Column(name = "lop_days", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal lopDays;

    @Column(name = "reverses_id", updatable = false)
    private UUID reversesId;

    @Column(name = "pay_input_id", updatable = false)
    private UUID payInputId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    public LeaveMonthlyLop() {}

    public LeaveMonthlyLop(
            UUID tenantId,
            UUID employeeId,
            String period,
            UUID leaveTypeId,
            UUID leaveRequestId,
            BigDecimal lopDays,
            UUID reversesId,
            UUID payInputId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId must not be null");
        this.period = Objects.requireNonNull(period, "period must not be null");
        this.leaveTypeId = leaveTypeId;
        this.leaveRequestId = leaveRequestId;
        this.lopDays = Objects.requireNonNull(lopDays, "lopDays must not be null");
        this.reversesId = reversesId;
        this.payInputId = payInputId;
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

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public UUID getLeaveTypeId() {
        return leaveTypeId;
    }

    public void setLeaveTypeId(UUID leaveTypeId) {
        this.leaveTypeId = leaveTypeId;
    }

    public UUID getLeaveRequestId() {
        return leaveRequestId;
    }

    public void setLeaveRequestId(UUID leaveRequestId) {
        this.leaveRequestId = leaveRequestId;
    }

    public BigDecimal getLopDays() {
        return lopDays;
    }

    public void setLopDays(BigDecimal lopDays) {
        this.lopDays = lopDays;
    }

    public UUID getReversesId() {
        return reversesId;
    }

    public void setReversesId(UUID reversesId) {
        this.reversesId = reversesId;
    }

    public UUID getPayInputId() {
        return payInputId;
    }

    public void setPayInputId(UUID payInputId) {
        this.payInputId = payInputId;
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
