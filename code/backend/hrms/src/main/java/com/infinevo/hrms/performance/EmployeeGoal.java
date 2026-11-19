package com.infinevo.hrms.performance;

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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Employee Goal & OKR Key Result (W-106.2) — {@code hrms.employee_goal}.
 */
@Entity
@Table(
        name = "employee_goal",
        schema = "hrms",
        indexes = {
            @Index(name = "idx_goal_tenant_emp_status", columnList = "tenant_id, employee_id, status"),
            @Index(name = "idx_goal_tenant_cycle", columnList = "tenant_id, cycle_id"),
            @Index(name = "idx_goal_tenant_due_date", columnList = "tenant_id, due_date")
        })
@Audited
public class EmployeeGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "cycle_id")
    private UUID cycleId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private GoalCategory category = GoalCategory.INDIVIDUAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false, length = 30)
    private MetricType metricType = MetricType.PERCENTAGE;

    @Column(name = "target_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal targetValue = new BigDecimal("100.00");

    @Column(name = "current_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal currentValue = BigDecimal.ZERO;

    @Column(name = "metric_unit", nullable = false, length = 30)
    private String metricUnit = "%";

    @Column(name = "progress_percentage", nullable = false)
    private int progressPercentage = 0;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private GoalStatus status = GoalStatus.IN_PROGRESS;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    protected EmployeeGoal() {}

    public EmployeeGoal(
            UUID tenantId,
            UUID employeeId,
            UUID cycleId,
            String title,
            String description,
            GoalCategory category,
            MetricType metricType,
            BigDecimal targetValue,
            BigDecimal currentValue,
            String metricUnit,
            Integer progressPercentage,
            LocalDate startDate,
            LocalDate dueDate,
            GoalStatus status,
            String actor) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId must not be null");
        this.cycleId = cycleId;
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.description = description;
        this.category = category != null ? category : GoalCategory.INDIVIDUAL;
        this.metricType = metricType != null ? metricType : MetricType.PERCENTAGE;
        this.targetValue = targetValue != null ? targetValue : new BigDecimal("100.00");
        this.currentValue = currentValue != null ? currentValue : BigDecimal.ZERO;
        this.metricUnit = (metricUnit != null && !metricUnit.isBlank()) ? metricUnit : "%";
        this.startDate = Objects.requireNonNull(startDate, "startDate must not be null");
        this.dueDate = Objects.requireNonNull(dueDate, "dueDate must not be null");
        this.status = status != null ? status : GoalStatus.IN_PROGRESS;

        if (this.dueDate.isBefore(this.startDate)) {
            throw new IllegalArgumentException("dueDate must not be before startDate");
        }

        this.progressPercentage =
                calculateProgress(this.metricType, this.targetValue, this.currentValue, progressPercentage);
        if (this.progressPercentage == 100 && this.status == GoalStatus.IN_PROGRESS) {
            this.status = GoalStatus.ACHIEVED;
        }

        String safeActor = (actor != null && !actor.isBlank()) ? actor : "system";
        this.createdBy = safeActor.length() > 100 ? safeActor.substring(0, 100) : safeActor;
        this.updatedBy = this.createdBy;
    }

    public void updateProgress(
            BigDecimal newCurrentValue, Integer explicitPercentage, GoalStatus newStatus, String actor) {
        if (newCurrentValue != null) {
            this.currentValue = newCurrentValue;
        }
        this.progressPercentage =
                calculateProgress(this.metricType, this.targetValue, this.currentValue, explicitPercentage);

        if (newStatus != null) {
            this.status = newStatus;
        } else if (this.progressPercentage >= 100 && this.status == GoalStatus.IN_PROGRESS) {
            this.status = GoalStatus.ACHIEVED;
        }

        String safeActor = (actor != null && !actor.isBlank()) ? actor : "system";
        this.updatedBy = safeActor.length() > 100 ? safeActor.substring(0, 100) : safeActor;
        this.updatedAt = Instant.now();
    }

    private static int calculateProgress(
            MetricType metricType, BigDecimal target, BigDecimal current, Integer explicit) {
        if (explicit != null) {
            return Math.clamp(explicit, 0, 100);
        }
        if (metricType == MetricType.MILESTONE) {
            return (current != null && current.compareTo(BigDecimal.ONE) >= 0) ? 100 : 0;
        }
        if (target != null && target.compareTo(BigDecimal.ZERO) > 0 && current != null) {
            BigDecimal pct = current.divide(target, 4, RoundingMode.HALF_UP).multiply(new BigDecimal(100));
            return Math.clamp(pct.intValue(), 0, 100);
        }
        return 0;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
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

    public UUID getCycleId() {
        return cycleId;
    }

    public void setCycleId(UUID cycleId) {
        this.cycleId = cycleId;
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

    public GoalCategory getCategory() {
        return category;
    }

    public MetricType getMetricType() {
        return metricType;
    }

    public BigDecimal getTargetValue() {
        return targetValue;
    }

    public BigDecimal getCurrentValue() {
        return currentValue;
    }

    public String getMetricUnit() {
        return metricUnit;
    }

    public int getProgressPercentage() {
        return progressPercentage;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public GoalStatus getStatus() {
        return status;
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
