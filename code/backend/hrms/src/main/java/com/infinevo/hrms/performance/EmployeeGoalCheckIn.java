package com.infinevo.hrms.performance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Historical record of an employee goal check-in (W-106.2).
 */
@Entity
@Table(
        name = "employee_goal_check_in",
        schema = "hrms",
        indexes = {@Index(name = "idx_goal_check_in_tenant_goal", columnList = "tenant_id, goal_id, created_at DESC")})
public class EmployeeGoalCheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "goal_id", nullable = false, updatable = false)
    private UUID goalId;

    @Column(name = "current_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal currentValue;

    @Column(name = "progress_percentage", nullable = false)
    private int progressPercentage;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    protected EmployeeGoalCheckIn() {}

    public EmployeeGoalCheckIn(
            UUID tenantId, UUID goalId, BigDecimal currentValue, int progressPercentage, String notes, String actor) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.goalId = Objects.requireNonNull(goalId, "goalId must not be null");
        this.currentValue = Objects.requireNonNull(currentValue, "currentValue must not be null");
        this.progressPercentage = progressPercentage;
        this.notes = notes != null && notes.length() > 2000 ? notes.substring(0, 2000) : notes;
        String safeActor = (actor != null && !actor.isBlank()) ? actor : "system";
        this.createdBy = safeActor.length() > 100 ? safeActor.substring(0, 100) : safeActor;
        this.createdAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getGoalId() {
        return goalId;
    }

    public BigDecimal getCurrentValue() {
        return currentValue;
    }

    public int getProgressPercentage() {
        return progressPercentage;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
