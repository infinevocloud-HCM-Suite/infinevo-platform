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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Approval definition entity representing a configured workflow for a specific flow type (W-15.1) — {@code core.approval_definition}.
 */
@Entity
@Table(
        name = "approval_definition",
        schema = "core",
        indexes = {
            @Index(name = "idx_approval_definition_lookup", columnList = "tenant_id, flow_type, effective_from DESC")
        })
@Audited
public class ApprovalDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "flow_type", nullable = false, length = 32)
    private ApprovalFlowType flowType;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_ordering", nullable = false, length = 16)
    private StepOrdering stepOrdering;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "steps", nullable = false, columnDefinition = "jsonb")
    private List<ApprovalStepDefinition> steps = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "comment_scope", nullable = false, length = 16)
    private CommentScope commentScope;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    protected ApprovalDefinition() {}

    public ApprovalDefinition(
            UUID tenantId,
            ApprovalFlowType flowType,
            StepOrdering stepOrdering,
            List<ApprovalStepDefinition> steps,
            CommentScope commentScope,
            boolean isActive,
            LocalDate effectiveFrom,
            String actor) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.flowType = Objects.requireNonNull(flowType, "flowType must not be null");
        this.stepOrdering = Objects.requireNonNull(stepOrdering, "stepOrdering must not be null");
        this.steps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
        this.commentScope = Objects.requireNonNull(commentScope, "commentScope must not be null");
        this.isActive = isActive;
        this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
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

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public ApprovalFlowType getFlowType() {
        return flowType;
    }

    public void setFlowType(ApprovalFlowType flowType) {
        this.flowType = Objects.requireNonNull(flowType, "flowType must not be null");
    }

    public StepOrdering getStepOrdering() {
        return stepOrdering;
    }

    public void setStepOrdering(StepOrdering stepOrdering) {
        this.stepOrdering = Objects.requireNonNull(stepOrdering, "stepOrdering must not be null");
    }

    public List<ApprovalStepDefinition> getSteps() {
        return steps;
    }

    public void setSteps(List<ApprovalStepDefinition> steps) {
        this.steps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
    }

    public CommentScope getCommentScope() {
        return commentScope;
    }

    public void setCommentScope(CommentScope commentScope) {
        this.commentScope = Objects.requireNonNull(commentScope, "commentScope must not be null");
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
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
        this.updatedBy = Objects.requireNonNull(updatedBy, "updatedBy must not be null");
    }
}
