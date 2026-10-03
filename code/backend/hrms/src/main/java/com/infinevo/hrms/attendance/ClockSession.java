package com.infinevo.hrms.attendance;

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
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Clock session entity mapping {@code hrms.clock_session} (W-40.3).
 *
 * <p>Not {@code @Audited} (spec §4, §13): the table is itself the event record and grows by several
 * rows per employee per day.
 */
@Entity
@Table(
        name = "clock_session",
        schema = "hrms",
        indexes = {
            @Index(
                    name = "idx_clock_session_tenant_employee_date",
                    columnList = "tenant_id, employee_id, attendance_date"),
            @Index(name = "idx_clock_session_tenant_date", columnList = "tenant_id, attendance_date")
        })
public class ClockSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "attendance_date", nullable = false, updatable = false)
    private LocalDate attendanceDate;

    @Column(name = "clock_in_at", nullable = false, updatable = false)
    private Instant clockInAt;

    @Column(name = "clock_out_at")
    private Instant clockOutAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 16, updatable = false)
    private SessionOrigin origin;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "void_reason", length = 24)
    private VoidReason voidReason;

    /** The regularization request that inserted this session (W-40.4); null for a clocked session. */
    @Column(name = "regularization_id", updatable = false)
    private UUID regularizationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    protected ClockSession() {}

    public ClockSession(
            UUID tenantId,
            UUID employeeId,
            LocalDate attendanceDate,
            Instant clockInAt,
            SessionOrigin origin,
            String actor) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId must not be null");
        this.attendanceDate = Objects.requireNonNull(attendanceDate, "attendanceDate must not be null");
        this.clockInAt = Objects.requireNonNull(clockInAt, "clockInAt must not be null");
        this.origin = origin != null ? origin : SessionOrigin.CLOCK;
        this.createdBy = actor != null ? actor : "system";
        this.updatedBy = this.createdBy;
    }

    /**
     * A closed session carrying an approved regularization's times (W-40.4 §4, the handler's step 3).
     */
    public static ClockSession regularized(
            UUID tenantId,
            UUID employeeId,
            LocalDate attendanceDate,
            Instant clockInAt,
            Instant clockOutAt,
            UUID regularizationId,
            String actor) {
        ClockSession session =
                new ClockSession(tenantId, employeeId, attendanceDate, clockInAt, SessionOrigin.REGULARIZATION, actor);
        session.clockOutAt = Objects.requireNonNull(clockOutAt, "clockOutAt must not be null");
        session.regularizationId = Objects.requireNonNull(regularizationId, "regularizationId must not be null");
        return session;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void close(Instant clockOutAt, String actor) {
        this.clockOutAt = Objects.requireNonNull(clockOutAt, "clockOutAt must not be null");
        this.updatedBy = actor != null ? actor : "system";
    }

    public void voidSession(VoidReason reason, Instant voidedAt, String actor) {
        this.voidReason = Objects.requireNonNull(reason, "reason must not be null");
        this.voidedAt = Objects.requireNonNull(voidedAt, "voidedAt must not be null");
        this.updatedBy = actor != null ? actor : "system";
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

    public LocalDate getAttendanceDate() {
        return attendanceDate;
    }

    public Instant getClockInAt() {
        return clockInAt;
    }

    public Instant getClockOutAt() {
        return clockOutAt;
    }

    public SessionOrigin getOrigin() {
        return origin;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public VoidReason getVoidReason() {
        return voidReason;
    }

    public UUID getRegularizationId() {
        return regularizationId;
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
