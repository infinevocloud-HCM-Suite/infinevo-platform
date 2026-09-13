package com.itsdev.payroll.dto.employeeitdeclaration;

import java.math.BigDecimal;

public class TaxSummaryDTO {

    private Long id;
    private String regime;                 // "with_exemptions", "without_exemptions"
    private Integer taxYearStart;
    private Integer taxYearEnd;

    private BigDecimal taxableIncome;
    private String taxableIncomeFormatted;

    private BigDecimal netTaxableIncome;
    private String netTaxableIncomeFormatted;

    private BigDecimal taxOnTaxableIncome;
    private String taxOnTaxableIncomeFormatted;

    private BigDecimal taxYtdAmount;
    private String taxYtdAmountFormatted;

    private BigDecimal taxToBePaid;
    private String taxToBePaidFormatted;

    private BigDecimal tdsThroughPayroll;
    private BigDecimal tdsPreviousEmployer;
    private BigDecimal tdsOtherIncome;

    private BigDecimal otherSourcesIncome;
    private BigDecimal exemptionUnderSection10;
    private BigDecimal exemptionUnderSection6A;

    private Integer noOfRemainingMonths;

    public TaxSummaryDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getTaxableIncomeFormatted() {
        return taxableIncomeFormatted;
    }

    public void setTaxableIncomeFormatted(String taxableIncomeFormatted) {
        this.taxableIncomeFormatted = taxableIncomeFormatted;
    }

    public BigDecimal getNetTaxableIncome() {
        return netTaxableIncome;
    }

    public void setNetTaxableIncome(BigDecimal netTaxableIncome) {
        this.netTaxableIncome = netTaxableIncome;
    }

    public String getNetTaxableIncomeFormatted() {
        return netTaxableIncomeFormatted;
    }

    public void setNetTaxableIncomeFormatted(String netTaxableIncomeFormatted) {
        this.netTaxableIncomeFormatted = netTaxableIncomeFormatted;
    }

    public BigDecimal getTaxOnTaxableIncome() {
        return taxOnTaxableIncome;
    }

    public void setTaxOnTaxableIncome(BigDecimal taxOnTaxableIncome) {
        this.taxOnTaxableIncome = taxOnTaxableIncome;
    }

    public String getTaxOnTaxableIncomeFormatted() {
        return taxOnTaxableIncomeFormatted;
    }

    public void setTaxOnTaxableIncomeFormatted(String taxOnTaxableIncomeFormatted) {
        this.taxOnTaxableIncomeFormatted = taxOnTaxableIncomeFormatted;
    }

    public BigDecimal getTaxYtdAmount() {
        return taxYtdAmount;
    }

    public void setTaxYtdAmount(BigDecimal taxYtdAmount) {
        this.taxYtdAmount = taxYtdAmount;
    }

    public String getTaxYtdAmountFormatted() {
        return taxYtdAmountFormatted;
    }

    public void setTaxYtdAmountFormatted(String taxYtdAmountFormatted) {
        this.taxYtdAmountFormatted = taxYtdAmountFormatted;
    }

    public BigDecimal getTaxToBePaid() {
        return taxToBePaid;
    }

    public void setTaxToBePaid(BigDecimal taxToBePaid) {
        this.taxToBePaid = taxToBePaid;
    }

    public String getTaxToBePaidFormatted() {
        return taxToBePaidFormatted;
    }

    public void setTaxToBePaidFormatted(String taxToBePaidFormatted) {
        this.taxToBePaidFormatted = taxToBePaidFormatted;
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
}
