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
import java.time.LocalDate;
import java.util.UUID;

/**
 * An assigned onboarding task instance for a specific employee (W-102).
 */
@Entity
@Audited
@Table(
        name = "employee_onboarding_task",
        schema = "core",
        indexes = {
            @Index(name = "idx_employee_onboarding_task_emp", columnList = "tenant_id, employee_id, status"),
            @Index(name = "idx_employee_onboarding_task_due", columnList = "tenant_id, due_date, status")
        })
public class EmployeeOnboardingTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "template_id")
    private UUID templateId;

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

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OnboardingTaskStatus status = OnboardingTaskStatus.PENDING;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by")
    private UUID completedBy;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    protected EmployeeOnboardingTask() {}

    public EmployeeOnboardingTask(
            UUID tenantId,
            UUID employeeId,
            UUID templateId,
            String title,
            String description,
            OnboardingTaskType taskType,
            String documentLabel,
            OnboardingAssignedRole assignedRole,
            boolean isMandatory,
            LocalDate dueDate,
            String actor) {
        this.tenantId = tenantId;
        this.employeeId = employeeId;
        this.templateId = templateId;
        this.title = title;
        this.description = description;
        this.taskType = taskType;
        this.documentLabel = documentLabel;
        this.assignedRole = assignedRole != null ? assignedRole : OnboardingAssignedRole.EMPLOYEE;
        this.isMandatory = isMandatory;
        this.dueDate = dueDate;
        this.status = OnboardingTaskStatus.PENDING;
        this.createdBy = actor != null ? actor : "system";
        this.updatedBy = this.createdBy;
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

    public UUID getEmployeeId() {
        return employeeId;
    }

    public UUID getTemplateId() {
        return templateId;
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

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public OnboardingTaskStatus getStatus() {
        return status;
    }

    public void setStatus(OnboardingTaskStatus status) {
        this.status = status;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public UUID getCompletedBy() {
        return completedBy;
    }

    public void setCompletedBy(UUID completedBy) {
        this.completedBy = completedBy;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
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
        this.updatedBy = updatedBy != null ? updatedBy : "system";
    }
}
