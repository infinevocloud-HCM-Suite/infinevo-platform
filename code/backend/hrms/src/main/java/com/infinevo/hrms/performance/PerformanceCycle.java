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
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Performance appraisal review cycle (W-106.1) — {@code hrms.performance_cycle}.
 */
@Entity
@Table(
        name = "performance_cycle",
        schema = "hrms",
        indexes = {
            @Index(name = "idx_perf_cycle_tenant_dates", columnList = "tenant_id, period_start DESC, period_end DESC")
        })
@Audited
public class PerformanceCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "self_review_deadline", nullable = false)
    private LocalDate selfReviewDeadline;

    @Column(name = "manager_review_deadline", nullable = false)
    private LocalDate managerReviewDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PerformanceCycleStatus status = PerformanceCycleStatus.DRAFT;

    @Column(name = "rating_scale_max", nullable = false)
    private int ratingScaleMax = 5;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    protected PerformanceCycle() {}

    public PerformanceCycle(
            UUID tenantId,
            String name,
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate selfReviewDeadline,
            LocalDate managerReviewDeadline,
            Integer ratingScaleMax,
            String actor) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.periodStart = Objects.requireNonNull(periodStart, "periodStart must not be null");
        this.periodEnd = Objects.requireNonNull(periodEnd, "periodEnd must not be null");
        this.selfReviewDeadline = Objects.requireNonNull(selfReviewDeadline, "selfReviewDeadline must not be null");
        this.managerReviewDeadline =
                Objects.requireNonNull(managerReviewDeadline, "managerReviewDeadline must not be null");
        this.ratingScaleMax = 5;
        this.status = PerformanceCycleStatus.DRAFT;
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

    public void activate(String actor) {
        if (this.status != PerformanceCycleStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT cycles can be activated");
        }
        this.status = PerformanceCycleStatus.ACTIVE;
        this.updatedBy = actor != null ? actor : "system";
        this.updatedAt = Instant.now();
    }

    public void close(String actor) {
        if (this.status != PerformanceCycleStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE cycles can be closed");
        }
        this.status = PerformanceCycleStatus.CLOSED;
        this.updatedBy = actor != null ? actor : "system";
        this.updatedAt = Instant.now();
    }

    public void updateDeadlines(LocalDate selfReviewDeadline, LocalDate managerReviewDeadline, String actor) {
        if (this.status == PerformanceCycleStatus.CLOSED) {
            throw new IllegalStateException("Cannot update deadlines for a CLOSED cycle");
        }
        if (selfReviewDeadline == null || managerReviewDeadline == null) {
            throw new IllegalArgumentException("selfReviewDeadline and managerReviewDeadline must not be null");
        }
        if (selfReviewDeadline.isAfter(managerReviewDeadline)) {
            throw new IllegalArgumentException("selfReviewDeadline must not be after managerReviewDeadline");
        }
        this.selfReviewDeadline = selfReviewDeadline;
        this.managerReviewDeadline = managerReviewDeadline;
        this.updatedBy = actor != null ? actor : "system";
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public LocalDate getSelfReviewDeadline() {
        return selfReviewDeadline;
    }

    public LocalDate getManagerReviewDeadline() {
        return managerReviewDeadline;
    }

    public PerformanceCycleStatus getStatus() {
        return status;
    }

    public int getRatingScaleMax() {
        return ratingScaleMax;
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
