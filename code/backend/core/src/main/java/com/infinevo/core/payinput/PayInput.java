package com.infinevo.core.payinput;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * One row of {@code core.pay_input} (W-19) — the append-only ledger every module writes a
 * pay-affecting value to, and the pay run reads.
 *
 * <p><strong>No setters.</strong> {@code app_user} holds no {@code UPDATE} on this table
 * ({@code V031}), so a setter here would compile and then fail at flush time with no clue why. A
 * correction is a new row: {@link PayInputService#reverse}, never an edit of this one — the rule
 * spec §4 states and the immutability {@code PayInputImmutabilityIT} proves at the database.
 */
@Entity
@Table(
        name = "pay_input",
        schema = "core",
        indexes = {
            @Index(
                    name = "idx_pay_input_tenant_employee_period_kind",
                    columnList = "tenant_id, employee_id, period, kind"),
            @Index(name = "idx_pay_input_tenant_period", columnList = "tenant_id, period"),
            @Index(name = "idx_pay_input_tenant_run", columnList = "tenant_id, run_ref")
        })
public class PayInput {

    public static final String ACTOR_SYSTEM = "system";

    /** {@code V191__pay_input_note.sql}: {@code note VARCHAR(200)}. */
    public static final int NOTE_MAX_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "period", nullable = false, length = 7, updatable = false)
    private YearMonth period;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32, updatable = false)
    private PayInputKind kind;

    @Column(name = "quantity", precision = 10, scale = 2, updatable = false)
    private BigDecimal quantity;

    @Column(name = "amount", precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "source_module", nullable = false, length = 16, updatable = false)
    private String sourceModule;

    @Column(name = "source_ref", length = 64, updatable = false)
    private String sourceRef;

    /** The run this row is tagged to, or {@code null} for the regular run (W-30.1). */
    @Column(name = "run_ref", updatable = false)
    private UUID runRef;

    /** The row this one reverses, or {@code null} for an original entry. */
    @Column(name = "reverses_id", updatable = false)
    private UUID reversesId;

    /**
     * The human reason for a hand-entered row, or a reversal's reason (D-140, {@code V191}). Display
     * only: it takes no part in idempotency, which is {@code sourceRef}'s job.
     */
    @Column(name = "note", length = NOTE_MAX_LENGTH, updatable = false)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = ACTOR_SYSTEM;

    @Column(name = "updated_at", nullable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100, updatable = false)
    private String updatedBy = ACTOR_SYSTEM;

    protected PayInput() {}

    /** Untagged — the regular run reads it. {@link #PayInput(UUID, UUID, YearMonth, PayInputKind, BigDecimal, BigDecimal, String, String, UUID, UUID, String) The full constructor} tags it to a run (W-30.1). */
    public PayInput(
            UUID tenantId,
            UUID employeeId,
            YearMonth period,
            PayInputKind kind,
            BigDecimal quantity,
            BigDecimal amount,
            String sourceModule,
            String sourceRef,
            UUID reversesId,
            String actor) {
        this(tenantId, employeeId, period, kind, quantity, amount, sourceModule, sourceRef, null, reversesId, actor);
    }

    public PayInput(
            UUID tenantId,
            UUID employeeId,
            YearMonth period,
            PayInputKind kind,
            BigDecimal quantity,
            BigDecimal amount,
            String sourceModule,
            String sourceRef,
            UUID runRef,
            UUID reversesId,
            String actor) {
        this(
                tenantId,
                employeeId,
                period,
                kind,
                quantity,
                amount,
                sourceModule,
                sourceRef,
                runRef,
                reversesId,
                actor,
                null);
    }

    /** As the run-tagged constructor, plus a human {@code note} (D-140). */
    public PayInput(
            UUID tenantId,
            UUID employeeId,
            YearMonth period,
            PayInputKind kind,
            BigDecimal quantity,
            BigDecimal amount,
            String sourceModule,
            String sourceRef,
            UUID runRef,
            UUID reversesId,
            String actor,
            String note) {
        this.tenantId = tenantId;
        this.employeeId = employeeId;
        this.period = period;
        this.kind = kind;
        this.quantity = quantity;
        this.amount = amount;
        this.sourceModule = sourceModule;
        this.sourceRef = sourceRef;
        this.runRef = runRef;
        this.reversesId = reversesId;
        this.note = note;
        this.createdBy = actor != null ? actor : ACTOR_SYSTEM;
        this.updatedBy = actor != null ? actor : ACTOR_SYSTEM;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
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

    public YearMonth getPeriod() {
        return period;
    }

    public PayInputKind getKind() {
        return kind;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getSourceModule() {
        return sourceModule;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    /** The run this row is tagged to, or {@code null} for the regular run (W-30.1). */
    public UUID getRunRef() {
        return runRef;
    }

    public UUID getReversesId() {
        return reversesId;
    }

    /** The human reason for this row, or {@code null} (D-140). */
    public String getNote() {
        return note;
    }

    /** Whether this row is a correction of another rather than an original entry. */
    public boolean isReversal() {
        return reversesId != null;
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
