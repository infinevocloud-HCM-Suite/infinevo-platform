package com.itsdev.payroll.entity.EmployeeITDeclaration;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_inv_tax_summary")
public class EmployeeInvTaxSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "declaration_id")
    private EmployeeInvestmentDeclaration declaration;

    @Column(name = "regime")
    private String regime;

    @Column(name = "tax_year_start")
    private Integer taxYearStart;

    @Column(name = "tax_year_end")
    private Integer taxYearEnd;

    @Column(name = "taxable_income", precision = 18, scale = 3)
    private BigDecimal taxableIncome = BigDecimal.ZERO;

    @Column(name = "net_taxable_income", precision = 18, scale = 3)
    private BigDecimal netTaxableIncome = BigDecimal.ZERO;

    @Column(name = "tax_on_taxable_income", precision = 18, scale = 3)
    private BigDecimal taxOnTaxableIncome = BigDecimal.ZERO;

    @Column(name = "tax_ytd_amount", precision = 18, scale = 3)
    private BigDecimal taxYtdAmount = BigDecimal.ZERO;

    @Column(name = "tax_to_be_paid", precision = 18, scale = 3)
    private BigDecimal taxToBePaid = BigDecimal.ZERO;

    @Column(name = "tds_through_payroll", precision = 18, scale = 3)
    private BigDecimal tdsThroughPayroll = BigDecimal.ZERO;

    @Column(name = "tds_previous_employer", precision = 18, scale = 3)
    private BigDecimal tdsPreviousEmployer = BigDecimal.ZERO;

    @Column(name = "tds_other_income", precision = 18, scale = 3)
    private BigDecimal tdsOtherIncome = BigDecimal.ZERO;

    @Column(name = "other_sources_income", precision = 18, scale = 3)
    private BigDecimal otherSourcesIncome = BigDecimal.ZERO;

    @Column(name = "exemption_under_section10", precision = 18, scale = 3)
    private BigDecimal exemptionUnderSection10 = BigDecimal.ZERO;

    @Column(name = "exemption_under_section6a", precision = 18, scale = 3)
    private BigDecimal exemptionUnderSection6A = BigDecimal.ZERO;

    @Column(name = "no_of_remaining_months")
    private Integer noOfRemainingMonths;

    @Column(name = "taxable_income_formatted")
    private String taxableIncomeFormatted;

    @Column(name = "net_taxable_income_formatted")
    private String netTaxableIncomeFormatted;

    @Column(name = "tax_on_taxable_income_formatted")
    private String taxOnTaxableIncomeFormatted;

    @Column(name = "tax_ytd_amount_formatted")
    private String taxYtdAmountFormatted;

    @Column(name = "tax_to_be_paid_formatted")
    private String taxToBePaidFormatted;

    @CreationTimestamp
    @Column(name = "created_time", updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    public EmployeeInvTaxSummary() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public EmployeeInvestmentDeclaration getDeclaration() {
        return declaration;
    }

    public void setDeclaration(EmployeeInvestmentDeclaration declaration) {
        this.declaration = declaration;
    }

    public String getRegime() {
        return regime;
    }

    public void setRegime(String regime) {
        this.regime = regime;
    }

    public Integer getTaxYearStart() {
        return taxYearStart;
    }

    public void setTaxYearStart(Integer taxYearStart) {
        this.taxYearStart = taxYearStart;
    }

    public Integer getTaxYearEnd() {
        return taxYearEnd;
    }

    public void setTaxYearEnd(Integer taxYearEnd) {
        this.taxYearEnd = taxYearEnd;
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

    public BigDecimal getExemptionUnderSection6A() {
        return exemptionUnderSection6A;
    }

    public void setExemptionUnderSection6A(BigDecimal exemptionUnderSection6A) {
        this.exemptionUnderSection6A = exemptionUnderSection6A;
    }

    public Integer getNoOfRemainingMonths() {
        return noOfRemainingMonths;
    }

    public void setNoOfRemainingMonths(Integer noOfRemainingMonths) {
        this.noOfRemainingMonths = noOfRemainingMonths;
    }

    public String getTaxableIncomeFormatted() {
        return taxableIncomeFormatted;
    }

    public void setTaxableIncomeFormatted(String taxableIncomeFormatted) {
        this.taxableIncomeFormatted = taxableIncomeFormatted;
    }

    public String getNetTaxableIncomeFormatted() {
        return netTaxableIncomeFormatted;
    }

    public void setNetTaxableIncomeFormatted(String netTaxableIncomeFormatted) {
        this.netTaxableIncomeFormatted = netTaxableIncomeFormatted;
    }

    public String getTaxOnTaxableIncomeFormatted() {
        return taxOnTaxableIncomeFormatted;
    }

    public void setTaxOnTaxableIncomeFormatted(String taxOnTaxableIncomeFormatted) {
        this.taxOnTaxableIncomeFormatted = taxOnTaxableIncomeFormatted;
    }

    public String getTaxYtdAmountFormatted() {
        return taxYtdAmountFormatted;
    }

    public void setTaxYtdAmountFormatted(String taxYtdAmountFormatted) {
        this.taxYtdAmountFormatted = taxYtdAmountFormatted;
    }

    public String getTaxToBePaidFormatted() {
        return taxToBePaidFormatted;
    }

    public void setTaxToBePaidFormatted(String taxToBePaidFormatted) {
        this.taxToBePaidFormatted = taxToBePaidFormatted;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }
}
