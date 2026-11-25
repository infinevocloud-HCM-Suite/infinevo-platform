package com.infinevo.payroll.taxcalc.recalc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An annual tax recalculation that failed and has not succeeded since (D-147, V222).
 *
 * <p>One open row per tenant, employee and financial year ({@code resolved_at} null). A repeat failure
 * updates it; a successful recalculation for the same employee and year closes it.
 */
@Entity
@Table(
        schema = "payroll",
        name = "tax_recalc_failure",
        indexes = {
            @Index(name = "uk_tax_recalc_failure_open", columnList = "tenant_id, employee_id, financial_year"),
            @Index(name = "idx_tax_recalc_failure_tenant_open", columnList = "tenant_id, last_failed_at")
        })
public class TaxRecalcFailure {

    /** The stored message is cut to this length; the full stack trace stays in the log. */
    static final int MAX_MESSAGE_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "financial_year", nullable = false, length = 9, updatable = false)
    private String financialYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"trigger\"", nullable = false, length = 24)
    private TaxTrigger trigger;

    @Column(name = "error_message", nullable = false)
    private String errorMessage;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "first_failed_at", nullable = false, updatable = false)
    private Instant firstFailedAt;

    @Column(name = "last_failed_at", nullable = false)
    private Instant lastFailedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100, updatable = false)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    protected TaxRecalcFailure() {}

    public TaxRecalcFailure(
            UUID tenantId,
            UUID employeeId,
            String financialYear,
            TaxTrigger trigger,
            String errorMessage,
            Instant failedAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId must not be null");
        this.financialYear = Objects.requireNonNull(financialYear, "financialYear must not be null");
        this.trigger = Objects.requireNonNull(trigger, "trigger must not be null");
        this.errorMessage = trim(errorMessage);
        this.attempts = 1;
        this.firstFailedAt = Objects.requireNonNull(failedAt, "failedAt must not be null");
        this.lastFailedAt = failedAt;
        this.createdAt = failedAt;
        this.updatedAt = failedAt;
    }

    /** Another failure for the same employee and year: the latest trigger and message win. */
    public void failedAgain(TaxTrigger trigger, String errorMessage, Instant failedAt) {
        this.trigger = Objects.requireNonNull(trigger, "trigger must not be null");
        this.errorMessage = trim(errorMessage);
        this.attempts = this.attempts + 1;
        this.lastFailedAt = Objects.requireNonNull(failedAt, "failedAt must not be null");
        this.updatedAt = failedAt;
    }

    static String trim(String message) {
        String m = (message == null || message.isBlank()) ? "Unknown error" : message;
        return m.length() > MAX_MESSAGE_LENGTH ? m.substring(0, MAX_MESSAGE_LENGTH) : m;
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

    public String getFinancialYear() {
        return financialYear;
    }

    public TaxTrigger getTrigger() {
        return trigger;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getFirstFailedAt() {
        return firstFailedAt;
    }

    public Instant getLastFailedAt() {
        return lastFailedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
