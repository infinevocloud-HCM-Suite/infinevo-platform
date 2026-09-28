package com.infinevo.core.attendance;

import com.infinevo.core.employee.Employee;
import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Attendance record for one employee on one date (W-39.1) — {@code core.attendance},
 * {@code migration/src/main/resources/db/migration/core/V030__attendance.sql}.
 */
@Entity
@Table(
        name = "attendance",
        schema = "core",
        indexes = {
            @Index(
                    name = "uk_attendance_tenant_employee_date",
                    columnList = "tenant_id, employee_id, attendance_date",
                    unique = true),
            @Index(name = "idx_attendance_tenant_date", columnList = "tenant_id, attendance_date DESC")
        })
@Audited
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AttendanceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private AttendanceSource source = AttendanceSource.ADMIN;

    @Column(name = "remarks", length = 255)
    private String remarks;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    protected Attendance() {}

    public Attendance(
            UUID tenantId,
            Employee employee,
            LocalDate attendanceDate,
            AttendanceStatus status,
            AttendanceSource source,
            String remarks,
            String actor) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.employee = Objects.requireNonNull(employee, "employee must not be null");
        this.attendanceDate = Objects.requireNonNull(attendanceDate, "attendanceDate must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.source = source != null ? source : AttendanceSource.ADMIN;
        this.remarks = remarks;
        this.createdBy = Objects.requireNonNull(actor, "actor must not be null");
        this.updatedBy = actor;
    }

    @PrePersist
    void onPersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void update(AttendanceStatus status, AttendanceSource source, String remarks, String actor) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (source != null) {
            this.source = source;
        }
        this.remarks = remarks;
        this.updatedBy = Objects.requireNonNull(actor, "actor must not be null");
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public Employee getEmployee() {
        return employee;
    }

    public LocalDate getAttendanceDate() {
        return attendanceDate;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public AttendanceSource getSource() {
        return source;
    }

    public String getRemarks() {
        return remarks;
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
}
