package com.infinevo.core.approval;

import com.infinevo.shared.audit.Audited;
import com.infinevo.shared.money.Money;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Approval step entity representing one step in an approval instance workflow (W-15.2) —
 * {@code core.approval_step}.
 */
@Entity
@Table(
        name = "approval_step",
        schema = "core",
        indexes = {
            @Index(name = "idx_approval_step_pending", columnList = "tenant_id, assignee_employee_id, decision"),
            @Index(name = "idx_approval_step_instance", columnList = "tenant_id, instance_id, step_index"),
            @Index(name = "idx_approval_step_escalation_sweep", columnList = "tenant_id, decision, created_at")
        })
@Audited
public class ApprovalStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "instance_id", nullable = false)
    private UUID instanceId;

    @Column(name = "step_index", nullable = false)
    private int stepIndex;

    @Column(name = "item_ref", length = 64)
    private String itemRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "approver_kind", nullable = false, length = 24)
    private ApproverKind approverKind;

    @Column(name = "assignee_employee_id")
    private UUID assigneeEmployeeId;

    @Column(name = "delegated_from_employee_id")
    private UUID delegatedFromEmployeeId;

    @Column(name = "escalated_from_employee_id")
    private UUID escalatedFromEmployeeId;

    @Column(name = "reassigned_from_employee_id")
    private UUID reassignedFromEmployeeId;

    @Column(name = "reassign_reason", length = 500)
    private String reassignReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", length = 16)
    private ApprovalDecision decision;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Column(name = "approved_amount", precision = 19, scale = 4)
    private BigDecimal approvedAmount;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    protected ApprovalStep() {}

    public ApprovalStep(
            UUID tenantId,
            UUID instanceId,
            int stepIndex,
            String itemRef,
            ApproverKind approverKind,
            UUID assigneeEmployeeId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId must not be null");
        this.stepIndex = stepIndex;
        this.itemRef = itemRef;
        this.approverKind = Objects.requireNonNull(approverKind, "approverKind must not be null");
        this.assigneeEmployeeId = assigneeEmployeeId;
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

    public UUID getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(UUID instanceId) {
        this.instanceId = instanceId;
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public void setStepIndex(int stepIndex) {
        this.stepIndex = stepIndex;
    }

    public String getItemRef() {
        return itemRef;
    }

    public void setItemRef(String itemRef) {
        this.itemRef = itemRef;
    }

    public ApproverKind getApproverKind() {
        return approverKind;
    }

    public void setApproverKind(ApproverKind approverKind) {
        this.approverKind = approverKind;
    }

    public UUID getAssigneeEmployeeId() {
        return assigneeEmployeeId;
    }

    public void setAssigneeEmployeeId(UUID assigneeEmployeeId) {
        this.assigneeEmployeeId = assigneeEmployeeId;
    }

    public UUID getDelegatedFromEmployeeId() {
        return delegatedFromEmployeeId;
    }

    public void setDelegatedFromEmployeeId(UUID delegatedFromEmployeeId) {
        this.delegatedFromEmployeeId = delegatedFromEmployeeId;
    }

    public UUID getEscalatedFromEmployeeId() {
        return escalatedFromEmployeeId;
    }

    public void setEscalatedFromEmployeeId(UUID escalatedFromEmployeeId) {
        this.escalatedFromEmployeeId = escalatedFromEmployeeId;
    }

    public UUID getReassignedFromEmployeeId() {
        return reassignedFromEmployeeId;
    }

    public void setReassignedFromEmployeeId(UUID reassignedFromEmployeeId) {
        this.reassignedFromEmployeeId = reassignedFromEmployeeId;
    }

    public String getReassignReason() {
        return reassignReason;
    }

    public void setReassignReason(String reassignReason) {
        this.reassignReason = reassignReason;
    }

    public ApprovalDecision getDecision() {
        return decision;
    }

    public void setDecision(ApprovalDecision decision) {
        this.decision = decision;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public void setApprovedAmount(BigDecimal approvedAmount) {
        this.approvedAmount = approvedAmount;
    }

    public Money getApprovedMoney() {
        return approvedAmount != null ? Money.of(approvedAmount) : null;
    }

    public void setApprovedMoney(Money money) {
        this.approvedAmount = money != null ? money.raw() : null;
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
