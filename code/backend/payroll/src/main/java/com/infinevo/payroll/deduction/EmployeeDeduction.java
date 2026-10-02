package com.infinevo.payroll.deduction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * One ad-hoc salary deduction (W-35.2 §6). Every field the officer entered is fixed at creation —
 * there is no setter for them, so no request can edit a deduction (§13 decision 2). The only change a
 * row ever sees is {@link #reverse}.
 *
 * <p>The id is minted by the service before the row is saved, because the ledger row written first
 * names it in its {@code source_ref}; {@link Persistable} tells Spring Data the row is new, so
 * {@code save} inserts instead of merging.
 */
@Entity
@Table(name = "employee_deduction", schema = "payroll")
public class EmployeeDeduction implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "period", nullable = false, length = 7, updatable = false)
    private String period;

    @Enumerated(EnumType.STRING)
    @Column(name = "deduction_type", nullable = false, length = 32, updatable = false)
    private DeductionType deductionType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "reason", nullable = false, length = 255, updatable = false)
    private String reason;

    @Column(name = "remarks", length = 500, updatable = false)
    private String remarks;

    @Column(name = "document_id", updatable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DeductionState status = DeductionState.POSTED;

    @Column(name = "pay_input_id", nullable = false)
    private UUID payInputId;

    @Column(name = "posted_period", nullable = false, length = 7)
    private String postedPeriod;

    @Column(name = "reversal_pay_input_id")
    private UUID reversalPayInputId;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reversed_by")
    private UUID reversedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 64, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy;

    @Transient
    private boolean isNew = true;

    protected EmployeeDeduction() {}

    /**
     * A {@code POSTED} deduction with the ledger row already written for it: the service records the
     * ledger row first, so the row is never saved without its {@code pay_input_id}.
     */
    public EmployeeDeduction(
            UUID id,
            UUID tenantId,
            UUID employeeId,
            YearMonth period,
            DeductionType deductionType,
            BigDecimal amount,
            String reason,
            String remarks,
            UUID documentId,
            UUID payInputId,
            YearMonth postedPeriod,
            String actor) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId must not be null");
        this.period = Objects.requireNonNull(period, "period must not be null").toString();
        this.deductionType = Objects.requireNonNull(deductionType, "deductionType must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
        this.remarks = remarks;
        this.documentId = documentId;
        this.payInputId = Objects.requireNonNull(payInputId, "payInputId must not be null");
        this.postedPeriod = Objects.requireNonNull(postedPeriod, "postedPeriod must not be null")
                .toString();
        this.createdBy = truncate(Objects.requireNonNull(actor, "actor must not be null"));
        this.updatedBy = this.createdBy;
    }

    /**
     * {@code POSTED → REVERSED}: the ledger row {@code reversalPayInputId} nets this one out. The row
     * stays — a reversal is a verb, never a delete (§9).
     *
     * @throws DeductionAlreadyReversedException if it is already reversed
     */
    public void reverse(UUID reversalPayInputId, UUID reversedBy, Instant at, String actor) {
        if (status == DeductionState.REVERSED) {
            throw new DeductionAlreadyReversedException(id);
        }
        this.status = DeductionState.REVERSED;
        this.reversalPayInputId = Objects.requireNonNull(reversalPayInputId, "reversalPayInputId must not be null");
        this.reversedAt = Objects.requireNonNull(at, "at must not be null");
        this.reversedBy = reversedBy;
        this.updatedBy = truncate(Objects.requireNonNull(actor, "actor must not be null"));
    }

    private static String truncate(String actor) {
        return actor.length() > 64 ? actor.substring(0, 64) : actor;
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

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @Override
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
        return YearMonth.parse(period);
    }

    public DeductionType getDeductionType() {
        return deductionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public String getRemarks() {
        return remarks;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public DeductionState getStatus() {
        return status;
    }

    public UUID getPayInputId() {
        return payInputId;
    }

    public YearMonth getPostedPeriod() {
        return YearMonth.parse(postedPeriod);
    }

    public UUID getReversalPayInputId() {
        return reversalPayInputId;
    }

    public Instant getReversedAt() {
        return reversedAt;
    }

    public UUID getReversedBy() {
        return reversedBy;
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
