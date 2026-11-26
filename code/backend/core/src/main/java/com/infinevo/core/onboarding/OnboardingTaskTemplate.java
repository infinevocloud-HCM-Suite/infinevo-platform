package com.infinevo.core.onboarding;

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
import java.util.UUID;

/**
 * Tenant-level onboarding checklist template item (W-102).
 */
@Entity
@Audited
@Table(
        name = "onboarding_task_template",
        schema = "core",
        indexes = {@Index(name = "idx_onboarding_template_tenant", columnList = "tenant_id, is_active, sort_order")})
public class OnboardingTaskTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 50)
    private OnboardingTaskType taskType;

    @Column(name = "document_label", length = 100)
    private String documentLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_role", nullable = false, length = 50)
    private OnboardingAssignedRole assignedRole = OnboardingAssignedRole.EMPLOYEE;

    @Column(name = "is_mandatory", nullable = false)
    private boolean isMandatory = true;

    @Column(name = "due_days_after_joining", nullable = false)
    private int dueDaysAfterJoining = 7;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    protected OnboardingTaskTemplate() {}

    public OnboardingTaskTemplate(
            UUID tenantId,
            String title,
            String description,
            OnboardingTaskType taskType,
            String documentLabel,
            OnboardingAssignedRole assignedRole,
            boolean isMandatory,
            int dueDaysAfterJoining,
            int sortOrder,
            String actor) {
        this.tenantId = tenantId;
        this.title = title;
        this.description = description;
        this.taskType = taskType;
        this.documentLabel = documentLabel;
        this.assignedRole = assignedRole != null ? assignedRole : OnboardingAssignedRole.EMPLOYEE;
        this.isMandatory = isMandatory;
        this.dueDaysAfterJoining = Math.max(0, dueDaysAfterJoining);
        this.sortOrder = sortOrder;
        this.isActive = true;
        this.createdBy = actor != null ? actor : "system";
        this.updatedBy = this.createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public OnboardingTaskType getTaskType() {
        return taskType;
    }

    public void setTaskType(OnboardingTaskType taskType) {
        this.taskType = taskType;
    }

    public String getDocumentLabel() {
        return documentLabel;
    }

    public void setDocumentLabel(String documentLabel) {
        this.documentLabel = documentLabel;
    }

    public OnboardingAssignedRole getAssignedRole() {
        return assignedRole;
    }

    public void setAssignedRole(OnboardingAssignedRole assignedRole) {
        this.assignedRole = assignedRole != null ? assignedRole : OnboardingAssignedRole.EMPLOYEE;
    }

    public boolean isMandatory() {
        return isMandatory;
    }

    public void setMandatory(boolean mandatory) {
        isMandatory = mandatory;
    }

    public int getDueDaysAfterJoining() {
        return dueDaysAfterJoining;
    }

    public void setDueDaysAfterJoining(int dueDaysAfterJoining) {
        this.dueDaysAfterJoining = Math.max(0, dueDaysAfterJoining);
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
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
