package com.infinevo.payroll.scheduled;

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
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * One row of {@code payroll.scheduled_earning} (W-73.6 §6): an earning an officer planned for a
 * future month, paid in one to twelve monthly instalments. What the officer entered — employee,
 * component, amount, first period, instalments, reason — is fixed at creation; only
 * {@link #status}, {@link #paidInstalments} and {@link #statusReason} move.
 *
 * <p>{@code first_period} is a {@code DATE} holding the first day of the month ({@code V161}
 * checks it); {@link #getFirstPeriod()} hands it back as the {@link YearMonth} the rest of payroll
 * speaks in.
 */
@Entity
@Table(
        name = "scheduled_earning",
        schema = "payroll",
        indexes = {
            @Index(name = "idx_scheduled_earning_tenant_status_period", columnList = "tenant_id, status, first_period"),
            @Index(name = "idx_scheduled_earning_tenant_employee", columnList = "tenant_id, employee_id")
        })
public class ScheduledEarning {

    public static final String ACTOR_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "earning_id", nullable = false, updatable = false)
    private UUID earningId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "first_period", nullable = false, updatable = false)
    private LocalDate firstPeriod;

    @Column(name = "instalments", nullable = false, updatable = false)
    private int instalments;

    @Column(name = "paid_instalments", nullable = false)
    private int paidInstalments = 0;

    /** First day of the month the latest instalment was paid into; {@code null} before the first. */
    @Column(name = "last_paid_period")
    private LocalDate lastPaidPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ScheduledEarningStatus status = ScheduledEarningStatus.SCHEDULED;

    @Column(name = "reason", length = 255, updatable = false)
    private String reason;

    /** Why it was paused or cancelled; {@code null} while scheduled or paid. */
    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ScheduledEarning() {}

    public ScheduledEarning(
            UUID tenantId,
            UUID employeeId,
            UUID earningId,
            BigDecimal amount,
            YearMonth firstPeriod,
            int instalments,
            String reason,
            String actor) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId must not be null");
        this.earningId = Objects.requireNonNull(earningId, "earningId must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.firstPeriod = Objects.requireNonNull(firstPeriod, "firstPeriod must not be null")
                .atDay(1);
        this.instalments = instalments;
        this.reason = reason;
        this.createdBy = actor != null ? actor : ACTOR_SYSTEM;
        this.updatedBy = this.createdBy;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * The earliest period the next unpaid instalment may be paid in: the first period, then the month
     * after the last one paid. Instalments are monthly and consecutive from wherever the last landed,
     * so one held up by a pause shifts the rest forward instead of piling into one month (W-73.6 F-1).
     */
    public YearMonth nextPeriod() {
        return lastPaidPeriod == null
                ? getFirstPeriod()
                : YearMonth.from(lastPaidPeriod).plusMonths(1);
    }

    /**
     * True when the row is waiting and its next instalment is due in or before {@code period} — an
     * overdue one (paused past its month, or added after that month's last run) is paid into
     * {@code period}. At most one instalment per period: once paid, the next is due a month later.
     */
    public boolean isDueIn(YearMonth period) {
        return status == ScheduledEarningStatus.SCHEDULED
                && paidInstalments < instalments
                && !nextPeriod().isAfter(period);
    }

    /** The instalment just written into {@code period}: counts it, and closes the row when it was the last. */
    public void markInstalmentPaid(YearMonth period, String actor) {
        if (status != ScheduledEarningStatus.SCHEDULED) {
            throw new IllegalStateException("Scheduled earning " + id + " is " + status + ", not SCHEDULED");
        }
        paidInstalments++;
        lastPaidPeriod =
                Objects.requireNonNull(period, "period must not be null").atDay(1);
        if (paidInstalments >= instalments) {
            status = ScheduledEarningStatus.PAID;
        }
        touch(actor);
    }

    public void pause(String reason, String actor) {
        status = ScheduledEarningStatus.PAUSED;
        statusReason = reason;
        touch(actor);
    }

    public void resume(String actor) {
        status = ScheduledEarningStatus.SCHEDULED;
        statusReason = null;
        touch(actor);
    }

    public void cancel(String reason, String actor) {
        status = ScheduledEarningStatus.CANCELLED;
        statusReason = reason;
        touch(actor);
    }

    private void touch(String actor) {
        this.updatedBy = actor != null ? actor : ACTOR_SYSTEM;
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

    public UUID getEarningId() {
        return earningId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public YearMonth getFirstPeriod() {
        return YearMonth.from(firstPeriod);
    }

    public int getInstalments() {
        return instalments;
    }

    public int getPaidInstalments() {
        return paidInstalments;
    }

    /** The month the latest instalment was paid into, or {@code null} before the first. */
    public YearMonth getLastPaidPeriod() {
        return lastPaidPeriod == null ? null : YearMonth.from(lastPaidPeriod);
    }

    public ScheduledEarningStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
