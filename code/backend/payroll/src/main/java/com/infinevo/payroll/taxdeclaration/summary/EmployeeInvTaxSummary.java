package com.infinevo.payroll.taxdeclaration.summary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Tax computation summary projection per declaration and regime (W-32.4).
 *
 * <p>Figures are written by the tax computation engine (W-33).
 */
@Entity
@Table(schema = "payroll", name = "employee_inv_tax_summary")
public class EmployeeInvTaxSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "declaration_id", nullable = false)
    private UUID declarationId;

    @Column(name = "regime", nullable = false, length = 3)
    private String regime;

    @Column(name = "taxable_income", precision = 19, scale = 4)
    private BigDecimal taxableIncome;

    @Column(name = "net_taxable_income", precision = 19, scale = 4)
    private BigDecimal netTaxableIncome;

    @Column(name = "tax_on_taxable_income", precision = 19, scale = 4)
    private BigDecimal taxOnTaxableIncome;

    @Column(name = "tax_ytd_amount", precision = 19, scale = 4)
    private BigDecimal taxYtdAmount;

    @Column(name = "tax_to_be_paid", precision = 19, scale = 4)
    private BigDecimal taxToBePaid;

    @Column(name = "tds_through_payroll", precision = 19, scale = 4)
    private BigDecimal tdsThroughPayroll;

    @Column(name = "tds_previous_employer", precision = 19, scale = 4)
    private BigDecimal tdsPreviousEmployer;

    @Column(name = "tds_other_income", precision = 19, scale = 4)
    private BigDecimal tdsOtherIncome;

    @Column(name = "other_sources_income", precision = 19, scale = 4)
    private BigDecimal otherSourcesIncome;

    @Column(name = "exemption_under_section10", precision = 19, scale = 4)
    private BigDecimal exemptionUnderSection10;

    @Column(name = "exemption_under_section6a", precision = 19, scale = 4)
    private BigDecimal exemptionUnderSection6a;

    @Column(name = "remaining_months")
    private Integer remainingMonths;

    @Column(name = "computed_at")
    private Instant computedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy = "system";

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    public EmployeeInvTaxSummary() {}

    public EmployeeInvTaxSummary(UUID tenantId, UUID declarationId, String regime) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.declarationId = Objects.requireNonNull(declarationId, "declarationId must not be null");
        this.regime = Objects.requireNonNull(regime, "regime must not be null");
    }

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

    public UUID getDeclarationId() {
        return declarationId;
    }

    public void setDeclarationId(UUID declarationId) {
        this.declarationId = declarationId;
    }

    public String getRegime() {
        return regime;
    }

    public void setRegime(String regime) {
        this.regime = regime;
    }

    public BigDecimal getTaxableIncome() {
        return taxableIncome;
    }

    public void setTaxableIncome(BigDecimal taxableIncome) {
        this.taxableIncome = taxableIncome;
    }

    public BigDecimal getNetTaxableIncome() {
        return netTaxableIncome;
    }

    public void setNetTaxableIncome(BigDecimal netTaxableIncome) {
        this.netTaxableIncome = netTaxableIncome;
    }

    public BigDecimal getTaxOnTaxableIncome() {
        return taxOnTaxableIncome;
    }

    public void setTaxOnTaxableIncome(BigDecimal taxOnTaxableIncome) {
        this.taxOnTaxableIncome = taxOnTaxableIncome;
    }

    public BigDecimal getTaxYtdAmount() {
        return taxYtdAmount;
    }

    public void setTaxYtdAmount(BigDecimal taxYtdAmount) {
        this.taxYtdAmount = taxYtdAmount;
    }

    public BigDecimal getTaxToBePaid() {
        return taxToBePaid;
    }

    public void setTaxToBePaid(BigDecimal taxToBePaid) {
        this.taxToBePaid = taxToBePaid;
    }

    public BigDecimal getTdsThroughPayroll() {
        return tdsThroughPayroll;
    }

    public void setTdsThroughPayroll(BigDecimal tdsThroughPayroll) {
        this.tdsThroughPayroll = tdsThroughPayroll;
    }

    public BigDecimal getTdsPreviousEmployer() {
        return tdsPreviousEmployer;
    }

    public void setTdsPreviousEmployer(BigDecimal tdsPreviousEmployer) {
        this.tdsPreviousEmployer = tdsPreviousEmployer;
    }

    public BigDecimal getTdsOtherIncome() {
        return tdsOtherIncome;
    }

    public void setTdsOtherIncome(BigDecimal tdsOtherIncome) {
        this.tdsOtherIncome = tdsOtherIncome;
    }

    public BigDecimal getOtherSourcesIncome() {
        return otherSourcesIncome;
    }

    public void setOtherSourcesIncome(BigDecimal otherSourcesIncome) {
        this.otherSourcesIncome = otherSourcesIncome;
    }

    public BigDecimal getExemptionUnderSection10() {
        return exemptionUnderSection10;
    }

    public void setExemptionUnderSection10(BigDecimal exemptionUnderSection10) {
        this.exemptionUnderSection10 = exemptionUnderSection10;
    }

    public BigDecimal getExemptionUnderSection6a() {
        return exemptionUnderSection6a;
    }

    public void setExemptionUnderSection6a(BigDecimal exemptionUnderSection6a) {
        this.exemptionUnderSection6a = exemptionUnderSection6a;
    }

    public Integer getRemainingMonths() {
        return remainingMonths;
    }

    public void setRemainingMonths(Integer remainingMonths) {
        this.remainingMonths = remainingMonths;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
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
