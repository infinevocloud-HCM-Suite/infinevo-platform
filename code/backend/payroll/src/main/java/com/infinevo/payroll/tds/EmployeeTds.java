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
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Annual Tax Deducted at Source (TDS) record per employee and financial year (W-36.1 §4, §6).
 */
@Entity
@Audited
@Table(
        name = "employee_tds",
        schema = "payroll",
        indexes = {
            @Index(
                    name = "idx_employee_tds_tenant_employee_fy",
                    columnList = "tenant_id, employee_id, financial_year, created_at DESC")
        })
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

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    public EmployeeTds() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public String getFinancialYear() {
        return financialYear;
    }

    public void setFinancialYear(String financialYear) {
        this.financialYear = financialYear;
    }

    public TaxRegime getRegime() {
        return regime;
    }

    public void setRegime(TaxRegime regime) {
        this.regime = regime;
    }

    public TdsSource getSource() {
        return source;
    }

    public void setSource(TdsSource source) {
        this.source = source;
    }

    public UUID getDeclarationId() {
        return declarationId;
    }

    public void setDeclarationId(UUID declarationId) {
        this.declarationId = declarationId;
    }

    public BigDecimal getAnnualGross() {
        return annualGross;
    }

    public void setAnnualGross(BigDecimal annualGross) {
        this.annualGross = annualGross;
    }

    public BigDecimal getAnnualTaxableIncome() {
        return annualTaxableIncome;
    }

    public void setAnnualTaxableIncome(BigDecimal annualTaxableIncome) {
        this.annualTaxableIncome = annualTaxableIncome;
    }

    public BigDecimal getAnnualTax() {
        return annualTax;
    }

    public void setAnnualTax(BigDecimal annualTax) {
        this.annualTax = annualTax;
    }

    public String getEffectiveFromPeriod() {
        return effectiveFromPeriod;
    }

    public void setEffectiveFromPeriod(String effectiveFromPeriod) {
        this.effectiveFromPeriod = effectiveFromPeriod;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public Instant getSupersededAt() {
        return supersededAt;
    }

    public void setSupersededAt(Instant supersededAt) {
        this.supersededAt = supersededAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
