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
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Performance appraisal review instance for an employee (W-106.1) — {@code hrms.performance_review}.
 */
@Entity
@Table(
        name = "performance_review",
        schema = "hrms",
        indexes = {
            @Index(
                    name = "uk_perf_review_tenant_cycle_emp",
                    columnList = "tenant_id, cycle_id, employee_id",
                    unique = true),
            @Index(name = "idx_perf_review_tenant_manager", columnList = "tenant_id, manager_id, status"),
            @Index(name = "idx_perf_review_tenant_emp", columnList = "tenant_id, employee_id, status")
        })
@Audited
public class PerformanceReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "cycle_id", nullable = false, updatable = false)
    private UUID cycleId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "manager_id")
    private UUID managerId;

    @Column(name = "self_rating")
    private Integer selfRating;

    @Column(name = "self_comments")
    private String selfComments;

    @Column(name = "self_submitted_at")
    private Instant selfSubmittedAt;

    @Column(name = "manager_rating")
    private Integer managerRating;

    @Column(name = "manager_comments")
    private String managerComments;

    @Column(name = "manager_submitted_at")
    private Instant managerSubmittedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PerformanceReviewStatus status = PerformanceReviewStatus.PENDING_SELF_REVIEW;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    protected PerformanceReview() {}

    public PerformanceReview(UUID tenantId, UUID cycleId, UUID employeeId, UUID managerId, String actor) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.cycleId = Objects.requireNonNull(cycleId, "cycleId must not be null");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId must not be null");
        this.managerId = managerId;
        this.status = PerformanceReviewStatus.PENDING_SELF_REVIEW;
        this.createdBy = actor != null ? actor : "system";
        this.updatedBy = actor != null ? actor : "system";
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
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
        if (createdBy == null) {
            createdBy = "system";
        }
        if (updatedBy == null) {
            updatedBy = "system";
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void submitSelfReview(int rating, String comments, String actor) {
        if (this.status != PerformanceReviewStatus.PENDING_SELF_REVIEW) {
            throw new IllegalStateException("Self review has already been submitted for this appraisal");
        }
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Self rating must be between 1 and 5");
        }
        this.selfRating = rating;
        this.selfComments = comments;
        this.selfSubmittedAt = Instant.now();
        this.status = PerformanceReviewStatus.PENDING_MANAGER_REVIEW;
        this.updatedBy = actor != null ? actor : "system";
        this.updatedAt = Instant.now();
    }

    public void submitManagerReview(int rating, String comments, String actor) {
        if (this.status != PerformanceReviewStatus.PENDING_MANAGER_REVIEW) {
            throw new IllegalStateException("Cannot evaluate review in status: " + this.status);
        }
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Manager rating must be between 1 and 5");
        }
        this.managerRating = rating;
        this.managerComments = comments;
        this.managerSubmittedAt = Instant.now();
        this.status = PerformanceReviewStatus.COMPLETED;
        this.updatedBy = actor != null ? actor : "system";
        this.updatedAt = Instant.now();
    }

    public void reassignManager(UUID newManagerId, String actor) {
        if (this.status == PerformanceReviewStatus.COMPLETED) {
            throw new IllegalStateException("Cannot reassign manager for completed review");
        }
        this.managerId = Objects.requireNonNull(newManagerId, "newManagerId must not be null");
        this.updatedBy = actor != null ? actor : "system";
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getCycleId() {
        return cycleId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public UUID getManagerId() {
        return managerId;
    }

    public Integer getSelfRating() {
        return selfRating;
    }

    public String getSelfComments() {
        return selfComments;
    }

    public Instant getSelfSubmittedAt() {
        return selfSubmittedAt;
    }

    public Integer getManagerRating() {
        return managerRating;
    }

    public String getManagerComments() {
        return managerComments;
    }

    public Instant getManagerSubmittedAt() {
        return managerSubmittedAt;
    }

    public PerformanceReviewStatus getStatus() {
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
