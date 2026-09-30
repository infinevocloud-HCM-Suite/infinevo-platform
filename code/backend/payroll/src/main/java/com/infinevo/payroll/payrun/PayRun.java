package com.infinevo.payroll.payrun;

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
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * One pay run for one tenant and period (W-29.1 §6). No money column lives here — W-29.2 adds
 * them. The status only moves through {@link #lock} and {@link #cancel}; there is no setter, so no
 * request body can set it (the legacy {@code PayRunServiceImpl.java:639-640} defect).
 */
@Entity
@Table(name = "payrun", schema = "payroll")
@Audited
public class PayRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "period", nullable = false, length = 7, updatable = false)
    private String period;

    @Column(name = "period_start", nullable = false, updatable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false, updatable = false)
    private LocalDate periodEnd;

    @Column(name = "cutoff_date", nullable = false, updatable = false)
    private LocalDate cutoffDate;

    @Column(name = "pay_date", nullable = false, updatable = false)
    private LocalDate payDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 16, updatable = false)
    private PayRunType runType = PayRunType.REGULAR;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PayRunStatus status = PayRunStatus.DRAFT;

    @Column(name = "included_count", nullable = false)
    private int includedCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by", length = 100)
    private String cancelledBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    protected PayRun() {}

    public PayRun(
            UUID tenantId,
            YearMonth period,
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate cutoffDate,
            LocalDate payDate,
            int includedCount,
            int skippedCount,
            String actor) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.period = Objects.requireNonNull(period, "period must not be null").toString();
        this.periodStart = Objects.requireNonNull(periodStart, "periodStart must not be null");
        this.periodEnd = Objects.requireNonNull(periodEnd, "periodEnd must not be null");
        this.cutoffDate = Objects.requireNonNull(cutoffDate, "cutoffDate must not be null");
        this.payDate = Objects.requireNonNull(payDate, "payDate must not be null");
        this.includedCount = includedCount;
        this.skippedCount = skippedCount;
        this.createdBy = Objects.requireNonNull(actor, "actor must not be null");
        this.updatedBy = actor;
    }

    /** {@code DRAFT → LOCKED}. The period lock itself is {@code PayInputService.lock}'s, called first. */
    public void lock(String actor, Instant at) {
        status.requireTransitionTo(PayRunStatus.LOCKED);
        this.status = PayRunStatus.LOCKED;
        this.lockedAt = Objects.requireNonNull(at, "at must not be null");
        this.lockedBy = Objects.requireNonNull(actor, "actor must not be null");
        this.updatedBy = actor;
    }

    /** {@code DRAFT → CANCELLED} or {@code LOCKED → CANCELLED}. A period lock is never undone (W-19 §6). */
    public void cancel(String actor, Instant at) {
        status.requireTransitionTo(PayRunStatus.CANCELLED);
        this.status = PayRunStatus.CANCELLED;
        this.cancelledAt = Objects.requireNonNull(at, "at must not be null");
        this.cancelledBy = Objects.requireNonNull(actor, "actor must not be null");
        this.updatedBy = actor;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public YearMonth getPeriod() {
        return YearMonth.parse(period);
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public LocalDate getCutoffDate() {
        return cutoffDate;
    }

    public LocalDate getPayDate() {
        return payDate;
    }

    public PayRunType getRunType() {
        return runType;
    }

    public PayRunStatus getStatus() {
        return status;
    }

    public int getIncludedCount() {
        return includedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelledBy() {
        return cancelledBy;
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
}
