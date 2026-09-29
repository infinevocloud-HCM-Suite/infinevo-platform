package com.infinevo.core.overtime;

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
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * One row of {@code core.overtime_request} (W-39.2) — a dated record of approved overtime hours
 * for an employee, entered by an administrator and written to the pay input ledger (W-19).
 *
 * <p>Unlike {@code PayInput}, this row is not immutable: {@link #markPosted} sets
 * {@code payInputId}/{@code postedPeriod} once the ledger has answered, in the same transaction the
 * row was inserted in (spec §6), and {@link #cancel} flips {@code status} once. Neither is an edit
 * of the values that were originally typed — there is no method that changes {@code hours},
 * {@code amount} or {@code employeeId} (spec §2: cancel and re-enter, never edit).
 */
@Entity
@Audited
@Table(
        name = "overtime_request",
        schema = "core",
        indexes = {
            @Index(name = "idx_overtime_request_tenant_date", columnList = "tenant_id, overtime_date"),
            @Index(
                    name = "idx_overtime_request_tenant_employee_date",
                    columnList = "tenant_id, employee_id, overtime_date"),
            @Index(name = "idx_overtime_request_tenant_pay_input", columnList = "tenant_id, pay_input_id")
        })
public class OvertimeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "overtime_date", nullable = false, updatable = false)
    private LocalDate overtimeDate;

    @Column(name = "hours", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal hours;

    @Column(name = "amount", precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OvertimeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16, updatable = false)
    private OvertimeSource source;

    @Column(name = "remarks", length = 255, updatable = false)
    private String remarks;

    /** Set by {@link #markPosted} once the ledger has answered; {@code null} until then. */
    @Column(name = "pay_input_id")
    private UUID payInputId;

    /** Set by {@link #markPosted}: the period the ledger actually posted to (spec §4 late input). */
    @Column(name = "posted_period", length = 7)
    private YearMonth postedPeriod;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    protected OvertimeRequest() {}

    public OvertimeRequest(
            UUID tenantId,
            UUID employeeId,
            LocalDate overtimeDate,
            BigDecimal hours,
            BigDecimal amount,
            OvertimeSource source,
            String remarks,
            String actor) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId must not be null");
        this.overtimeDate = Objects.requireNonNull(overtimeDate, "overtimeDate must not be null");
        this.hours = Objects.requireNonNull(hours, "hours must not be null");
        this.amount = amount;
        this.status = OvertimeStatus.APPROVED;
        this.source = source != null ? source : OvertimeSource.ADMIN;
        this.remarks = remarks;
        this.createdBy = actor;
        this.updatedBy = actor;
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

    /** Records the ledger's answer, in the same transaction the row was inserted in (spec §6). */
    public void markPosted(UUID payInputId, YearMonth postedPeriod, String actor) {
        this.payInputId = Objects.requireNonNull(payInputId, "payInputId must not be null");
        this.postedPeriod = Objects.requireNonNull(postedPeriod, "postedPeriod must not be null");
        this.updatedBy = actor;
    }

    /** Cancels this entry. Refuse a second cancel at the service layer — this method does not check. */
    public void cancel(String actor) {
        this.status = OvertimeStatus.CANCELLED;
        this.cancelledAt = Instant.now();
        this.updatedBy = actor;
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

    public LocalDate getOvertimeDate() {
        return overtimeDate;
    }

    public BigDecimal getHours() {
        return hours;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public OvertimeStatus getStatus() {
        return status;
    }

    public OvertimeSource getSource() {
        return source;
    }

    public String getRemarks() {
        return remarks;
    }

    public UUID getPayInputId() {
        return payInputId;
    }

    public YearMonth getPostedPeriod() {
        return postedPeriod;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
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
