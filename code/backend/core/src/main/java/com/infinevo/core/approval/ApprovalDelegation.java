package com.infinevo.core.approval;

import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Approval delegation entity representing a temporary delegation of approval authority (W-15.3) —
 * {@code core.approval_delegation}.
 */
@Entity
@Table(
        name = "approval_delegation",
        schema = "core",
        indexes = {
            @Index(
                    name = "idx_approval_delegation_tenant_lookup",
                    columnList = "tenant_id, delegator_employee_id, effective_from, effective_to"),
            @Index(name = "idx_approval_delegation_tenant_delegate", columnList = "tenant_id, delegate_employee_id")
        })
@Audited
public class ApprovalDelegation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "delegator_employee_id", nullable = false)
    private UUID delegatorEmployeeId;

    @Column(name = "delegate_employee_id", nullable = false)
    private UUID delegateEmployeeId;

    @Column(name = "flow_types", length = 256)
    private String flowTypes;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to", nullable = false)
    private LocalDate effectiveTo;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    protected ApprovalDelegation() {}

    public ApprovalDelegation(
            UUID tenantId,
            UUID delegatorEmployeeId,
            UUID delegateEmployeeId,
            String flowTypes,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.delegatorEmployeeId = Objects.requireNonNull(delegatorEmployeeId, "delegatorEmployeeId must not be null");
        this.delegateEmployeeId = Objects.requireNonNull(delegateEmployeeId, "delegateEmployeeId must not be null");
        if (delegatorEmployeeId.equals(delegateEmployeeId)) {
            throw new IllegalArgumentException("delegator and delegate must not be the same employee");
        }
        this.flowTypes = flowTypes;
        this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
        this.effectiveTo = Objects.requireNonNull(effectiveTo, "effectiveTo must not be null");
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new IllegalArgumentException("effectiveFrom must not be after effectiveTo");
        }
        this.isActive = true;
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

    public boolean coversFlow(ApprovalFlowType flowType) {
        if (flowTypes == null || flowTypes.isBlank()) {
            return true;
        }
        if (flowType == null) {
            return false;
        }
        return Arrays.stream(flowTypes.split(",")).map(String::trim).anyMatch(s -> s.equalsIgnoreCase(flowType.name()));
    }

    public boolean isActiveOn(LocalDate date) {
        if (!isActive || date == null) {
            return false;
        }
        return !date.isBefore(effectiveFrom) && !date.isAfter(effectiveTo);
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

    public UUID getDelegatorEmployeeId() {
        return delegatorEmployeeId;
    }

    public void setDelegatorEmployeeId(UUID delegatorEmployeeId) {
        this.delegatorEmployeeId = delegatorEmployeeId;
    }

    public UUID getDelegateEmployeeId() {
        return delegateEmployeeId;
    }

    public void setDelegateEmployeeId(UUID delegateEmployeeId) {
        this.delegateEmployeeId = delegateEmployeeId;
    }

    public String getFlowTypes() {
        return flowTypes;
    }

    public void setFlowTypes(String flowTypes) {
        this.flowTypes = flowTypes;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
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

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
