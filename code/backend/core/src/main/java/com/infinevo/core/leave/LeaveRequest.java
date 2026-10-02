package com.infinevo.core.leave;

import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing an employee's leave request (W-16.3).
 */
@Entity
@Audited
@Table(
        name = "leave_request",
        schema = "core",
        indexes = {
            @Index(name = "idx_leave_request_employee_date", columnList = "tenant_id, employee_id, from_date DESC"),
            @Index(name = "idx_leave_request_status", columnList = "tenant_id, status")
        })
public class LeaveRequest {

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

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(name = "is_half_day", nullable = false)
    private boolean isHalfDay;

    @Convert(converter = HalfDayPeriod.JpaConverter.class)
    @Column(name = "half_day_period", length = 8)
    private HalfDayPeriod halfDayPeriod;

    @Column(name = "working_days", nullable = false, precision = 10, scale = 2)
    private BigDecimal workingDays;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private LeaveRequestStatus status;

    @Column(name = "approval_instance_id")
    private UUID approvalInstanceId;

    @Column(name = "on_behalf", nullable = false)
    private boolean onBehalf;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    public LeaveRequest() {}

    public LeaveRequest(
            UUID tenantId,
            UUID employeeId,
            UUID leaveTypeId,
            LocalDate fromDate,
            LocalDate toDate,
            boolean isHalfDay,
            HalfDayPeriod halfDayPeriod,
            BigDecimal workingDays,
            String reason,
            LeaveRequestStatus status,
            UUID approvalInstanceId,
            boolean onBehalf) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId must not be null");
        this.leaveTypeId = Objects.requireNonNull(leaveTypeId, "leaveTypeId must not be null");
        this.fromDate = Objects.requireNonNull(fromDate, "fromDate must not be null");
        this.toDate = Objects.requireNonNull(toDate, "toDate must not be null");
        this.isHalfDay = isHalfDay;
        this.halfDayPeriod = halfDayPeriod;
        this.workingDays = Objects.requireNonNull(workingDays, "workingDays must not be null");
        this.reason = reason;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.approvalInstanceId = approvalInstanceId;
        this.onBehalf = onBehalf;
        validateHalfDayPair();
    }

    public void validateHalfDayPair() {
        if (isHalfDay && halfDayPeriod == null) {
            throw new IllegalArgumentException("halfDayPeriod is required when isHalfDay is true");
        }
        if (!isHalfDay && halfDayPeriod != null) {
            throw new IllegalArgumentException("halfDayPeriod must be null when isHalfDay is false");
        }
    }

    @PrePersist
    public void onPrePersist() {
        validateHalfDayPair();
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
    }

    @PreUpdate
    public void onPreUpdate() {
        validateHalfDayPair();
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

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public boolean isHalfDay() {
        return isHalfDay;
    }

    public void setHalfDay(boolean halfDay) {
        isHalfDay = halfDay;
        validateHalfDayPair();
    }

    public HalfDayPeriod getHalfDayPeriod() {
        return halfDayPeriod;
    }

    public void setHalfDayPeriod(HalfDayPeriod halfDayPeriod) {
        this.halfDayPeriod = halfDayPeriod;
        validateHalfDayPair();
    }

    public BigDecimal getWorkingDays() {
        return workingDays;
    }

    public void setWorkingDays(BigDecimal workingDays) {
        this.workingDays = workingDays;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LeaveRequestStatus getStatus() {
        return status;
    }

    public void setStatus(LeaveRequestStatus status) {
        this.status = status;
    }

    public UUID getApprovalInstanceId() {
        return approvalInstanceId;
    }

    public void setApprovalInstanceId(UUID approvalInstanceId) {
        this.approvalInstanceId = approvalInstanceId;
    }

    public boolean isOnBehalf() {
        return onBehalf;
    }

    public void setOnBehalf(boolean onBehalf) {
        this.onBehalf = onBehalf;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
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
