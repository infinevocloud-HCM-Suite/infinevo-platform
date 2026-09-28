package com.infinevo.core.approval;

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
import java.util.Objects;
import java.util.UUID;

/**
 * Approval instance entity representing a single item awaiting approval through the engine (W-15.2) —
 * {@code core.approval_instance}.
 */
@Entity
@Table(
        name = "approval_instance",
        schema = "core",
        indexes = {
            @Index(name = "idx_approval_instance_lookup", columnList = "tenant_id, subject_table, subject_id"),
            @Index(name = "idx_approval_instance_status", columnList = "tenant_id, status")
        })
@Audited
public class ApprovalInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "flow_type", nullable = false, length = 32)
    private ApprovalFlowType flowType;

    @Column(name = "definition_id", nullable = false)
    private UUID definitionId;

    @Column(name = "subject_table", nullable = false, length = 64)
    private String subjectTable;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "subject_employee_id", nullable = false)
    private UUID subjectEmployeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private InstanceStatus status;

    @Column(name = "outcome_notified_at")
    private Instant outcomeNotifiedAt;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    protected ApprovalInstance() {}

    public ApprovalInstance(
            UUID tenantId, ApprovalFlowType flowType, UUID definitionId, SubjectRef subject, UUID subjectEmployeeId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.flowType = Objects.requireNonNull(flowType, "flowType must not be null");
        this.definitionId = Objects.requireNonNull(definitionId, "definitionId must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        this.subjectTable = subject.table();
        this.subjectId = subject.id();
        this.subjectEmployeeId = Objects.requireNonNull(subjectEmployeeId, "subjectEmployeeId must not be null");
        this.status = InstanceStatus.PENDING;
        this.startedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (startedAt == null) {
            startedAt = now;
        }
        if (status == null) {
            status = InstanceStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
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

    public ApprovalFlowType getFlowType() {
        return flowType;
    }

    public void setFlowType(ApprovalFlowType flowType) {
        this.flowType = flowType;
    }

    public UUID getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(UUID definitionId) {
        this.definitionId = definitionId;
    }

    public String getSubjectTable() {
        return subjectTable;
    }

    public void setSubjectTable(String subjectTable) {
        this.subjectTable = subjectTable;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(UUID subjectId) {
        this.subjectId = subjectId;
    }

    public UUID getSubjectEmployeeId() {
        return subjectEmployeeId;
    }

    public void setSubjectEmployeeId(UUID subjectEmployeeId) {
        this.subjectEmployeeId = subjectEmployeeId;
    }

    public InstanceStatus getStatus() {
        return status;
    }

    public void setStatus(InstanceStatus status) {
        this.status = status;
    }

    public Instant getOutcomeNotifiedAt() {
        return outcomeNotifiedAt;
    }

    public void setOutcomeNotifiedAt(Instant outcomeNotifiedAt) {
        this.outcomeNotifiedAt = outcomeNotifiedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
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

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
