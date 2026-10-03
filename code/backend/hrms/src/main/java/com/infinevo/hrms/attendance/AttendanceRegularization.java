package com.infinevo.hrms.attendance;

import com.infinevo.shared.audit.Audited;
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
 * An employee's request to correct one day's clock times, mapping {@code hrms.attendance_regularization} (W-40.4).
 *
 * <p>Decided through the {@code REGULARIZATION} approval flow; {@link RegularizationOutcomeHandler} writes the
 * outcome. Changes are audited to {@code core.audit_log}.
 */
@Entity
@Table(
        name = "attendance_regularization",
        schema = "hrms",
        indexes = {
            @Index(
                    name = "idx_attendance_regularization_tenant_employee_date",
                    columnList = "tenant_id, employee_id, attendance_date"),
            @Index(
                    name = "idx_attendance_regularization_tenant_status_date",
                    columnList = "tenant_id, status, attendance_date"),
            @Index(
                    name = "idx_attendance_regularization_tenant_instance",
                    columnList = "tenant_id, approval_instance_id")
        })
@Audited
public class AttendanceRegularization {

    /** Length of {@code reason} and {@code decision_comment}. */
    public static final int MAX_TEXT = 500;

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

    @Column(name = "requested_in_at", nullable = false, updatable = false)
    private Instant requestedInAt;

    @Column(name = "requested_out_at", nullable = false, updatable = false)
    private Instant requestedOutAt;

    @Column(name = "reason", nullable = false, length = MAX_TEXT, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RegularizationStatus status = RegularizationStatus.PENDING;

    @Column(name = "approval_instance_id")
    private UUID approvalInstanceId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decision_comment", length = MAX_TEXT)
    private String decisionComment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    protected AttendanceRegularization() {}

    public AttendanceRegularization(
            UUID tenantId,
            UUID employeeId,
            LocalDate attendanceDate,
            Instant requestedInAt,
            Instant requestedOutAt,
            String reason,
            String actor) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId must not be null");
        this.attendanceDate = Objects.requireNonNull(attendanceDate, "attendanceDate must not be null");
        this.requestedInAt = Objects.requireNonNull(requestedInAt, "requestedInAt must not be null");
        this.requestedOutAt = Objects.requireNonNull(requestedOutAt, "requestedOutAt must not be null");
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
        this.createdBy = actor != null ? actor : "system";
        this.updatedBy = this.createdBy;
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

    /** Links the approval instance started for this request, in the submit's transaction. */
    public void attachInstance(UUID approvalInstanceId) {
        this.approvalInstanceId = Objects.requireNonNull(approvalInstanceId, "approvalInstanceId must not be null");
    }

    /** Records the flow's outcome. Only a {@code PENDING} request is decided; the handler checks first. */
    public void decide(RegularizationStatus outcome, Instant decidedAt, UUID decidedBy, String comment) {
        if (outcome == null || outcome == RegularizationStatus.PENDING) {
            throw new IllegalArgumentException("outcome must be APPROVED or REJECTED");
        }
        this.status = outcome;
        this.decidedAt = Objects.requireNonNull(decidedAt, "decidedAt must not be null");
        this.decidedBy = decidedBy;
        this.decisionComment =
                comment == null ? null : comment.length() > MAX_TEXT ? comment.substring(0, MAX_TEXT) : comment;
        this.updatedBy = "system";
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

    public Instant getRequestedInAt() {
        return requestedInAt;
    }

    public Instant getRequestedOutAt() {
        return requestedOutAt;
    }

    public String getReason() {
        return reason;
    }

    public RegularizationStatus getStatus() {
        return status;
    }

    public UUID getApprovalInstanceId() {
        return approvalInstanceId;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public String getDecisionComment() {
        return decisionComment;
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
