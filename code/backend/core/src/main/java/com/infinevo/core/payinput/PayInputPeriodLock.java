package com.infinevo.core.payinput;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * One row of {@code core.pay_input_period_lock} (W-19) — a period locked for a tenant. Its mere
 * existence is what {@code trg_pay_input_period_lock} ({@code V032}) checks before every insert
 * into {@code core.pay_input}; nothing in Java re-implements that rule; the trigger is the
 * enforcement and this entity is only how the service reads and writes the row that drives it.
 *
 * <p>No setters, the same reason as {@link PayInput}: {@code app_user} holds no {@code UPDATE} or
 * {@code DELETE} here either. A lock is never undone by the application (spec §6).
 */
@Entity
@Table(name = "pay_input_period_lock", schema = "core")
public class PayInputPeriodLock {

    public static final String ACTOR_SYSTEM = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "period", nullable = false, length = 7, updatable = false)
    private YearMonth period;

    /** The run this lock applies to, or {@code null} for a whole-period lock (W-30.1). */
    @Column(name = "run_ref", updatable = false)
    private UUID runRef;

    @Column(name = "locked_at", nullable = false, updatable = false)
    private Instant lockedAt;

    @Column(name = "locked_by", nullable = false, length = 100, updatable = false)
    private String lockedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100, updatable = false)
    private String updatedBy = ACTOR_SYSTEM;

    protected PayInputPeriodLock() {}

    public PayInputPeriodLock(UUID tenantId, YearMonth period, String lockedBy) {
        this(tenantId, period, null, lockedBy);
    }

    /** A run lock — {@code period} is still stored, for reporting, alongside the run it applies to (W-30.1). */
    public PayInputPeriodLock(UUID tenantId, YearMonth period, UUID runRef, String lockedBy) {
        this.tenantId = tenantId;
        this.period = period;
        this.runRef = runRef;
        this.lockedBy = lockedBy != null ? lockedBy : ACTOR_SYSTEM;
        this.createdBy = this.lockedBy;
        this.updatedBy = this.lockedBy;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        lockedAt = now;
        createdAt = now;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public YearMonth getPeriod() {
        return period;
    }

    /** The run this lock applies to, or {@code null} for a whole-period lock (W-30.1). */
    public UUID getRunRef() {
        return runRef;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public String getLockedBy() {
        return lockedBy;
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
