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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * One pay run for one tenant and period (W-29.1 §6), with the run totals W-29.2 adds. The status
 * only moves through {@link #lock}, {@link #cancel} and the three computation methods; there is no
 * setter, so no request body can set it (the legacy {@code PayRunServiceImpl.java:639-640} defect).
 */
@Entity
@Table(name = "payrun", schema = "payroll")
@Audited
public class PayRun {

    /** Zero at the stored scale, so a new or reset row reads back exactly as it was written. */
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(4);

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

    @Column(name = "notes", length = 500, updatable = false)
    private String notes;

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

    @Column(name = "total_gross", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalGross = ZERO_AMOUNT;

    @Column(name = "total_deductions", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalDeductions = ZERO_AMOUNT;

    @Column(name = "total_net_pay", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalNetPay = ZERO_AMOUNT;

    @Column(name = "negative_net_count", nullable = false)
    private int negativeNetCount;

    @Column(name = "computed_at")
    private Instant computedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "job_id", length = 64)
    private String jobId;

    @Column(name = "compute_attempt", nullable = false)
    private int computeAttempt;

    @Column(name = "compute_started_at")
    private Instant computeStartedAt;

    @Column(name = "progress_done", nullable = false)
    private int progressDone;

    @Column(name = "progress_total", nullable = false)
    private int progressTotal;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "paid_by", length = 100)
    private String paidBy;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    @Column(name = "payslips_released_at")
    private Instant payslipsReleasedAt;

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

    /**
     * A run of another type (W-30.2) — an off-cycle run: the regular run's dates from the schedule,
     * except the pay date, which is the one the officer gave; a note of up to 500 characters.
     */
    public static PayRun ofType(
            PayRunType runType,
            UUID tenantId,
            YearMonth period,
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate cutoffDate,
            LocalDate payDate,
            String notes,
            int includedCount,
            int skippedCount,
            String actor) {
        String note = notes == null || notes.isBlank() ? null : notes.strip();
        if (note != null && note.length() > 500) {
            throw new IllegalArgumentException("notes must be at most 500 characters");
        }
        PayRun run = new PayRun(
                tenantId, period, periodStart, periodEnd, cutoffDate, payDate, includedCount, skippedCount, actor);
        run.runType = Objects.requireNonNull(runType, "runType must not be null");
        run.notes = note;
        return run;
    }

    /**
     * {@code DRAFT → LOCKED}. The pay input lock itself — the period's, or an off-cycle run's own — is
     * {@code PayInputService}'s, called first.
     */
    public void lock(String actor, Instant at) {
        status.requireTransitionTo(PayRunStatus.LOCKED);
        this.status = PayRunStatus.LOCKED;
        this.lockedAt = Objects.requireNonNull(at, "at must not be null");
        this.lockedBy = Objects.requireNonNull(actor, "actor must not be null");
        this.updatedBy = actor;
    }

    /**
     * {@code DRAFT}, {@code LOCKED} or {@code APPROVED → CANCELLED}. A period lock is never undone
     * (W-19 §6). A {@code PAID} run is never cancelled (W-36.2 §10, founder 2026-10-01): the money moved,
     * and cancelling would free the month for a second paid run.
     */
    public void cancel(String actor, Instant at) {
        status.requireTransitionTo(PayRunStatus.CANCELLED);
        this.status = PayRunStatus.CANCELLED;
        this.cancelledAt = Objects.requireNonNull(at, "at must not be null");
        this.cancelledBy = Objects.requireNonNull(actor, "actor must not be null");
        this.updatedBy = actor;
    }

    /** {@code LOCKED | COMPUTED | FAILED → COMPUTING} (W-29.2 §3). Clears the last failure reason. */
    public void startComputing(String actor) {
        status.requireTransitionTo(PayRunStatus.COMPUTING);
        this.status = PayRunStatus.COMPUTING;
        this.failureReason = null;
        this.updatedBy = Objects.requireNonNull(actor, "actor must not be null");
    }

    /**
     * Starts the next computation attempt on a run already {@code COMPUTING} (W-29.4 §3): the caller
     * has judged the previous attempt abandoned. No transition — the run stays {@code COMPUTING}.
     *
     * @throws IllegalPayRunTransitionException unless the run is {@code COMPUTING}
     */
    public void resumeComputing(String actor) {
        if (status != PayRunStatus.COMPUTING) {
            throw new IllegalPayRunTransitionException(status, PayRunStatus.COMPUTING);
        }
        this.failureReason = null;
        this.updatedBy = Objects.requireNonNull(actor, "actor must not be null");
    }

    /**
     * Numbers a new attempt and names the job that runs it (W-29.4 §3). {@code alreadyDone} is the
     * count of rows a resumed attempt keeps; zero for a fresh one.
     *
     * @return the new attempt number
     */
    public int beginAttempt(String jobIdPrefix, int alreadyDone, Instant at) {
        Objects.requireNonNull(jobIdPrefix, "jobIdPrefix must not be null");
        this.computeAttempt++;
        this.jobId = jobIdPrefix + computeAttempt;
        this.computeStartedAt = Objects.requireNonNull(at, "at must not be null");
        this.progressDone = alreadyDone;
        this.progressTotal = includedCount;
        return computeAttempt;
    }

    /** Employees computed so far in the current attempt. */
    public void reportProgress(int done) {
        this.progressDone = done;
    }

    /** {@code COMPUTING → COMPUTED}, with the run totals summed over its rows. */
    public void completeComputation(
            BigDecimal totalGross,
            BigDecimal totalDeductions,
            BigDecimal totalNetPay,
            int negativeNetCount,
            String actor,
            Instant at) {
        status.requireTransitionTo(PayRunStatus.COMPUTED);
        this.status = PayRunStatus.COMPUTED;
        applyTotals(totalGross, totalDeductions, totalNetPay, negativeNetCount, actor, at);
    }

    /**
     * {@code COMPUTING → FAILED}: at least one employee could not be computed. The totals are those of
     * the rows that did compute; {@code reason} says how many did not.
     */
    public void failComputation(
            String reason,
            BigDecimal totalGross,
            BigDecimal totalDeductions,
            BigDecimal totalNetPay,
            int negativeNetCount,
            String actor,
            Instant at) {
        status.requireTransitionTo(PayRunStatus.FAILED);
        this.status = PayRunStatus.FAILED;
        this.failureReason = truncate(Objects.requireNonNull(reason, "reason must not be null"));
        applyTotals(totalGross, totalDeductions, totalNetPay, negativeNetCount, actor, at);
    }

    private void applyTotals(
            BigDecimal totalGross,
            BigDecimal totalDeductions,
            BigDecimal totalNetPay,
            int negativeNetCount,
            String actor,
            Instant at) {
        this.negativeNetCount = negativeNetCount;
        this.totalGross = Objects.requireNonNull(totalGross, "totalGross must not be null");
        this.totalDeductions = Objects.requireNonNull(totalDeductions, "totalDeductions must not be null");
        this.totalNetPay = Objects.requireNonNull(totalNetPay, "totalNetPay must not be null");
        this.computedAt = Objects.requireNonNull(at, "at must not be null");
        this.updatedBy = Objects.requireNonNull(actor, "actor must not be null");
    }

    private static String truncate(String text) {
        return text.length() > 500 ? text.substring(0, 500) : text;
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

    public String getNotes() {
        return notes;
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

    public BigDecimal getTotalGross() {
        return totalGross;
    }

    public BigDecimal getTotalDeductions() {
        return totalDeductions;
    }

    public BigDecimal getTotalNetPay() {
        return totalNetPay;
    }

    /** Rows whose net pay came out negative in the last computation (W-29.3 §13 decision 4). */
    public int getNegativeNetCount() {
        return negativeNetCount;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getJobId() {
        return jobId;
    }

    public int getComputeAttempt() {
        return computeAttempt;
    }

    public Instant getComputeStartedAt() {
        return computeStartedAt;
    }

    public int getProgressDone() {
        return progressDone;
    }

    public int getProgressTotal() {
        return progressTotal;
    }

    /** {@code COMPUTED → APPROVED}: officer signs off on the computed figures. */
    public void approve(String actor, Instant at) {
        status.requireTransitionTo(PayRunStatus.APPROVED);
        this.status = PayRunStatus.APPROVED;
        this.approvedAt = Objects.requireNonNull(at, "at must not be null");
        this.approvedBy = Objects.requireNonNull(actor, "actor must not be null");
        this.updatedBy = actor;
    }

    /**
     * {@code APPROVED → PAID}: marks the run paid and releases payslips.
     *
     * @param paidOn the payment date; must not be in the future, and not before periodStart
     * @param actor who recorded the payment
     * @param at when the transition occurred
     */
    public void pay(LocalDate paidOn, String actor, Instant at) {
        status.requireTransitionTo(PayRunStatus.PAID);
        Objects.requireNonNull(paidOn, "paidOn must not be null");
        LocalDate today = LocalDate.now();
        if (paidOn.isAfter(today)) {
            throw new IllegalArgumentException("paid_on cannot be in the future: " + paidOn);
        }
        if (paidOn.isBefore(periodStart)) {
            throw new IllegalArgumentException(
                    "paid_on cannot be before period start (" + periodStart + "): " + paidOn);
        }
        this.status = PayRunStatus.PAID;
        this.paidAt = Objects.requireNonNull(at, "at must not be null");
        this.paidBy = Objects.requireNonNull(actor, "actor must not be null");
        this.paidOn = paidOn;
        this.payslipsReleasedAt = at;
        this.updatedBy = actor;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public String getPaidBy() {
        return paidBy;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public Instant getPayslipsReleasedAt() {
        return payslipsReleasedAt;
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
