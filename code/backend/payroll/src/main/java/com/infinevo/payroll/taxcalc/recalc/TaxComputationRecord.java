package com.infinevo.payroll.taxcalc.recalc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Immutable audit entity recording an annual income tax recalculation snapshot (W-33.3).
 *
 * <p>Append-only history: rows are never updated or deleted.
 */
@Entity
@Table(schema = "payroll", name = "tax_computation")
public class TaxComputationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "declaration_id")
    private UUID declarationId;

    @Column(name = "financial_year", nullable = false, length = 9)
    private String financialYear;

    @Column(name = "regime", nullable = false, length = 3)
    private String regime;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"trigger\"", nullable = false, length = 24)
    private TaxTrigger trigger;

    @Column(name = "gross_total_income", nullable = false, precision = 19, scale = 4)
    private BigDecimal grossTotalIncome;

    @Column(name = "hra_exemption", nullable = false, precision = 19, scale = 4)
    private BigDecimal hraExemption;

    @Column(name = "standard_deduction", nullable = false, precision = 19, scale = 4)
    private BigDecimal standardDeduction;

    @Column(name = "professional_tax", nullable = false, precision = 19, scale = 4)
    private BigDecimal professionalTax;

    @Column(name = "house_property_income", nullable = false, precision = 19, scale = 4)
    private BigDecimal housePropertyIncome;

    @Column(name = "other_income", nullable = false, precision = 19, scale = 4)
    private BigDecimal otherIncome;

    @Column(name = "chapter_via", nullable = false, precision = 19, scale = 4)
    private BigDecimal chapterVia;

    @Column(name = "taxable_income", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxableIncome;

    @Column(name = "tax_before_rebate", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxBeforeRebate;

    @Column(name = "rebate", nullable = false, precision = 19, scale = 4)
    private BigDecimal rebate;

    @Column(name = "surcharge", nullable = false, precision = 19, scale = 4)
    private BigDecimal surcharge;

    @Column(name = "cess", nullable = false, precision = 19, scale = 4)
    private BigDecimal cess;

    @Column(name = "prev_employer_tds", nullable = false, precision = 19, scale = 4)
    private BigDecimal prevEmployerTds;

    @Column(name = "annual_tax", nullable = false, precision = 19, scale = 4)
    private BigDecimal annualTax;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "working", nullable = false, columnDefinition = "jsonb")
    private String working;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Column(name = "computed_by")
    private UUID computedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    protected TaxComputationRecord() {}

    public TaxComputationRecord(
            UUID tenantId,
            UUID employeeId,
            UUID declarationId,
            String financialYear,
            String regime,
            TaxTrigger trigger,
            BigDecimal grossTotalIncome,
            BigDecimal hraExemption,
            BigDecimal standardDeduction,
            BigDecimal professionalTax,
            BigDecimal housePropertyIncome,
            BigDecimal otherIncome,
            BigDecimal chapterVia,
            BigDecimal taxableIncome,
            BigDecimal taxBeforeRebate,
            BigDecimal rebate,
            BigDecimal surcharge,
            BigDecimal cess,
            BigDecimal prevEmployerTds,
            BigDecimal annualTax,
            String working,
            Instant computedAt,
            UUID computedBy,
            String createdBy) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId must not be null");
        this.declarationId = declarationId;
        this.financialYear = Objects.requireNonNull(financialYear, "financialYear must not be null");
        this.regime = Objects.requireNonNull(regime, "regime must not be null");
        this.trigger = Objects.requireNonNull(trigger, "trigger must not be null");
        this.grossTotalIncome = Objects.requireNonNull(grossTotalIncome, "grossTotalIncome must not be null");
        this.hraExemption = Objects.requireNonNull(hraExemption, "hraExemption must not be null");
        this.standardDeduction = Objects.requireNonNull(standardDeduction, "standardDeduction must not be null");
        this.professionalTax = Objects.requireNonNull(professionalTax, "professionalTax must not be null");
        this.housePropertyIncome = Objects.requireNonNull(housePropertyIncome, "housePropertyIncome must not be null");
        this.otherIncome = Objects.requireNonNull(otherIncome, "otherIncome must not be null");
        this.chapterVia = Objects.requireNonNull(chapterVia, "chapterVia must not be null");
        this.taxableIncome = Objects.requireNonNull(taxableIncome, "taxableIncome must not be null");
        this.taxBeforeRebate = Objects.requireNonNull(taxBeforeRebate, "taxBeforeRebate must not be null");
        this.rebate = Objects.requireNonNull(rebate, "rebate must not be null");
        this.surcharge = Objects.requireNonNull(surcharge, "surcharge must not be null");
        this.cess = Objects.requireNonNull(cess, "cess must not be null");
        this.prevEmployerTds = Objects.requireNonNull(prevEmployerTds, "prevEmployerTds must not be null");
        this.annualTax = Objects.requireNonNull(annualTax, "annualTax must not be null");
        this.working = Objects.requireNonNull(working, "working must not be null");
        this.computedAt = computedAt != null ? computedAt : Instant.now();
        this.computedBy = computedBy;
        this.createdBy = createdBy != null ? createdBy : "system";
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

    public UUID getDeclarationId() {
        return declarationId;
    }

    public String getFinancialYear() {
        return financialYear;
    }

    public String getRegime() {
        return regime;
    }

    public TaxTrigger getTrigger() {
        return trigger;
    }

    public BigDecimal getGrossTotalIncome() {
        return grossTotalIncome;
    }

    public BigDecimal getHraExemption() {
        return hraExemption;
    }

    public BigDecimal getStandardDeduction() {
        return standardDeduction;
    }

    public BigDecimal getProfessionalTax() {
        return professionalTax;
    }

    public BigDecimal getHousePropertyIncome() {
        return housePropertyIncome;
    }

    public BigDecimal getOtherIncome() {
        return otherIncome;
    }

    public BigDecimal getChapterVia() {
        return chapterVia;
    }

    public BigDecimal getTaxableIncome() {
        return taxableIncome;
    }

    public BigDecimal getTaxBeforeRebate() {
        return taxBeforeRebate;
    }

    public BigDecimal getRebate() {
        return rebate;
    }

    public BigDecimal getSurcharge() {
        return surcharge;
    }

    public BigDecimal getCess() {
        return cess;
    }

    public BigDecimal getPrevEmployerTds() {
        return prevEmployerTds;
    }

    public BigDecimal getAnnualTax() {
        return annualTax;
    }

    public String getWorking() {
        return working;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public UUID getComputedBy() {
        return computedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
