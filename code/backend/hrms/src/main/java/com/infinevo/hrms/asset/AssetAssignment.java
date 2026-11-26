package com.infinevo.hrms.asset;

import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "asset_assignment", schema = "hrms")
@Audited
public class AssetAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "assigned_date", nullable = false)
    private LocalDate assignedDate;

    @Column(name = "assigned_by", nullable = false, length = 100)
    private String assignedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_condition", nullable = false, length = 32)
    private AssetCondition assignmentCondition = AssetCondition.GOOD;

    @Column(name = "assignment_notes", columnDefinition = "TEXT")
    private String assignmentNotes;

    @Column(name = "returned_date")
    private LocalDate returnedDate;

    @Column(name = "returned_by", length = 100)
    private String returnedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "return_condition", length = 32)
    private AssetCondition returnCondition;

    @Column(name = "return_notes", columnDefinition = "TEXT")
    private String returnNotes;

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

    @PrePersist
    void onPrePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (createdBy == null || createdBy.isBlank()) {
            createdBy = "system";
        }
        if (updatedBy == null || updatedBy.isBlank()) {
            updatedBy = "system";
        }
        if (assignmentCondition == null) {
            assignmentCondition = AssetCondition.GOOD;
        }
    }

    @PreUpdate
    void onPreUpdate() {
        updatedAt = Instant.now();
        if (updatedBy == null || updatedBy.isBlank()) {
            updatedBy = "system";
        }
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

    public UUID getAssetId() {
        return assetId;
    }

    public void setAssetId(UUID assetId) {
        this.assetId = assetId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public LocalDate getAssignedDate() {
        return assignedDate;
    }

    public void setAssignedDate(LocalDate assignedDate) {
        this.assignedDate = assignedDate;
    }

    public String getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(String assignedBy) {
        this.assignedBy = assignedBy;
    }

    public AssetCondition getAssignmentCondition() {
        return assignmentCondition;
    }

    public void setAssignmentCondition(AssetCondition assignmentCondition) {
        this.assignmentCondition = assignmentCondition;
    }

    public String getAssignmentNotes() {
        return assignmentNotes;
    }

    public void setAssignmentNotes(String assignmentNotes) {
        this.assignmentNotes = assignmentNotes;
    }

    public LocalDate getReturnedDate() {
        return returnedDate;
    }

    public void setReturnedDate(LocalDate returnedDate) {
        this.returnedDate = returnedDate;
    }

    public String getReturnedBy() {
        return returnedBy;
    }

    public void setReturnedBy(String returnedBy) {
        this.returnedBy = returnedBy;
    }

    public AssetCondition getReturnCondition() {
        return returnCondition;
    }

    public void setReturnCondition(AssetCondition returnCondition) {
        this.returnCondition = returnCondition;
    }

    public String getReturnNotes() {
        return returnNotes;
    }

    public void setReturnNotes(String returnNotes) {
        this.returnNotes = returnNotes;
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

