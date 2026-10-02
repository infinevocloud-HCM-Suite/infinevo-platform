package com.infinevo.payroll.tds;

import com.infinevo.payroll.taxcalc.TaxRegime;
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
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Annual tax record per employee per financial year (W-36.1 §4, §6).
 *
 * <p>Superseded not edited: when a figure changes, the active row is marked {@code is_active = false}
 * with {@code superseded_at = clock_timestamp()}, and a new active row is inserted.
 * Replaces legacy employee_tds (legacy/docs/DB_SCHEMA.md:1374-1391, EmployeeTds.java:8-51).
 */
@Entity
@Table(name = "employee_tds", schema = "payroll")
@Audited
public class EmployeeTds {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "financial_year", nullable = false, length = 9, updatable = false)
    private String financialYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "regime", nullable = false, length = 3, updatable = false)
    private TaxRegime regime;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16, updatable = false)
    private TdsSource source;

    @Column(name = "declaration_id", updatable = false)
    private UUID declarationId;

    @Column(name = "annual_gross", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal annualGross;

    @Column(name = "annual_taxable_income", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal annualTaxableIncome;

    @Column(name = "annual_tax", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal annualTax;

    @Column(name = "effective_from_period", nullable = false, length = 7, updatable = false)
    private String effectiveFromPeriod;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "note", length = 255, updatable = false)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 64, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy;

    protected EmployeeTds() {}

    public EmployeeTds(
            UUID tenantId,
            UUID employeeId,
            String financialYear,
            TaxRegime regime,
            TdsSource source,
            UUID declarationId,
            BigDecimal annualGross,
            BigDecimal annualTaxableIncome,
            BigDecimal annualTax,
            String effectiveFromPeriod,
            String note,
            String actor,
            Instant now) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId must not be null");
        this.financialYear = Objects.requireNonNull(financialYear, "financialYear must not be null");
        this.regime = Objects.requireNonNull(regime, "regime must not be null");
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.declarationId = declarationId;
        this.annualGross = Objects.requireNonNull(annualGross, "annualGross must not be null");
        this.annualTaxableIncome = Objects.requireNonNull(annualTaxableIncome, "annualTaxableIncome must not be null");
        this.annualTax = Objects.requireNonNull(annualTax, "annualTax must not be null");
        this.effectiveFromPeriod = Objects.requireNonNull(effectiveFromPeriod, "effectiveFromPeriod must not be null");
        this.note = note;
        this.isActive = true;
        this.supersededAt = null;
        Instant timestamp = now != null ? now : Instant.now().truncatedTo(ChronoUnit.MICROS);
        String createdActor = actor != null ? actor : "system";
        this.createdAt = timestamp;
        this.createdBy = createdActor;
        this.updatedAt = timestamp;
        this.updatedBy = createdActor;
    }

    /**
     * Supersedes this active record: marks {@code is_active = false} and sets {@code superseded_at}.
     */
    public void supersede(Instant at, String actor) {
        this.isActive = false;
        this.supersededAt = Objects.requireNonNull(at, "at must not be null");
        this.updatedAt = at;
        this.updatedBy = Objects.requireNonNull(actor, "actor must not be null");
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.createdBy == null) {
            this.createdBy = "system";
        }
        if (this.updatedBy == null) {
            this.updatedBy = this.createdBy;
        }
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

    public UUID getEmployeeId() {
        return employeeId;
    }

    public String getFinancialYear() {
        return financialYear;
    }

    public TaxRegime getRegime() {
        return regime;
    }

    public TdsSource getSource() {
        return source;
    }

    public UUID getDeclarationId() {
        return declarationId;
    }

    public BigDecimal getAnnualGross() {
        return annualGross;
    }

    public BigDecimal getAnnualTaxableIncome() {
        return annualTaxableIncome;
    }

    public BigDecimal getAnnualTax() {
        return annualTax;
    }

    public String getEffectiveFromPeriod() {
        return effectiveFromPeriod;
    }

    public boolean isActive() {
        return isActive;
    }

    public Instant getSupersededAt() {
        return supersededAt;
    }

    public String getNote() {
        return note;
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
