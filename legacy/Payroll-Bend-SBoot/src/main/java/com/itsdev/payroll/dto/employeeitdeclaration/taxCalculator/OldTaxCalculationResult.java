package com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public class OldTaxCalculationResult {

    /* ==========================
       INCOME
       ========================== */
    private BigDecimal grossIncome;
    private BigDecimal incomeFromSalary;
    private BigDecimal incomeFromHouseProperty;
    private BigDecimal incomeFromOtherSources;

    /* ==========================
       EXEMPTIONS & DEDUCTIONS
       ========================== */
    private BigDecimal hraExemption;
    private BigDecimal totalChapterVIA;

    // Section-wise breakup (HRA, 80C, 80D, etc.)
    private Map<String, BigDecimal> sectionWiseDeductions =
            new LinkedHashMap<>();

    /* ==========================
       TAX CALCULATION
       ========================== */
    private BigDecimal taxableIncome;
    private BigDecimal taxBeforeRebate;
    private BigDecimal rebateAmount;
    private BigDecimal surcharge;
    private BigDecimal cess;
    private BigDecimal taxPayable;
    private BigDecimal standardDeduction;

    private BigDecimal previousEmploymentIncome;
    private BigDecimal tdsByPreviousEmployer;



    /* ==========================
       PAYROLL (TDS)
       ========================== */
    private BigDecimal taxPerMonth;
    private Integer remainingMonths;

    /* ==========================
       CONSTRUCTORS
       ========================== */
    public OldTaxCalculationResult() {
    }

    /* ==========================
       SECTION HELPERS
       ========================== */
    public void addSectionDeduction(String section, BigDecimal amount) {
        if (section != null && amount != null) {
            this.sectionWiseDeductions.put(section, amount);
        }
    }

    /* ==========================
       GETTERS
       ========================== */

    public void setSectionWiseDeductions(Map<String, BigDecimal> sectionWiseDeductions) {
        this.sectionWiseDeductions = sectionWiseDeductions;
    }

    public BigDecimal getPreviousEmploymentIncome() {
        return previousEmploymentIncome;
    }

    public void setPreviousEmploymentIncome(BigDecimal previousEmploymentIncome) {
        this.previousEmploymentIncome = previousEmploymentIncome;
    }

    public BigDecimal getTdsByPreviousEmployer() {
        return tdsByPreviousEmployer;
    }

    public void setTdsByPreviousEmployer(BigDecimal tdsByPreviousEmployer) {
        this.tdsByPreviousEmployer = tdsByPreviousEmployer;
    }

    public BigDecimal getStandardDeduction() {
        return standardDeduction;
    }

    public void setStandardDeduction(BigDecimal standardDeduction) {
        this.standardDeduction = standardDeduction;
    }

    public BigDecimal getGrossIncome() {
        return grossIncome;
    }

    public BigDecimal getIncomeFromSalary() {
        return incomeFromSalary;
    }

    public BigDecimal getIncomeFromHouseProperty() {
        return incomeFromHouseProperty;
    }

    public BigDecimal getIncomeFromOtherSources() {
        return incomeFromOtherSources;
    }

    public BigDecimal getHraExemption() {
        return hraExemption;
    }

    public BigDecimal getTotalChapterVIA() {
        return totalChapterVIA;
    }

    public Map<String, BigDecimal> getSectionWiseDeductions() {
        return sectionWiseDeductions;
    }

    public BigDecimal getTaxableIncome() {
        return taxableIncome;
    }

    public BigDecimal getTaxBeforeRebate() {
        return taxBeforeRebate;
    }

    public BigDecimal getRebateAmount() {
        return rebateAmount;
    }

    public BigDecimal getSurcharge() {
        return surcharge;
    }

    public BigDecimal getCess() {
        return cess;
    }

    public BigDecimal getTaxPayable() {
        return taxPayable;
    }

    public BigDecimal getTaxPerMonth() {
        return taxPerMonth;
    }

    public Integer getRemainingMonths() {
        return remainingMonths;
    }

    /* ==========================
       SETTERS
       ========================== */
    public void setGrossIncome(BigDecimal grossIncome) {
        this.grossIncome = grossIncome;
    }

    public void setIncomeFromSalary(BigDecimal incomeFromSalary) {
        this.incomeFromSalary = incomeFromSalary;
    }

    public void setIncomeFromHouseProperty(BigDecimal incomeFromHouseProperty) {
        this.incomeFromHouseProperty = incomeFromHouseProperty;
    }

    public void setIncomeFromOtherSources(BigDecimal incomeFromOtherSources) {
        this.incomeFromOtherSources = incomeFromOtherSources;
    }

    public void setHraExemption(BigDecimal hraExemption) {
        this.hraExemption = hraExemption;
    }

    public void setTotalChapterVIA(BigDecimal totalChapterVIA) {
        this.totalChapterVIA = totalChapterVIA;
    }

    public void setTaxableIncome(BigDecimal taxableIncome) {
        this.taxableIncome = taxableIncome;
    }

    public void setTaxBeforeRebate(BigDecimal taxBeforeRebate) {
        this.taxBeforeRebate = taxBeforeRebate;
    }

    public void setRebateAmount(BigDecimal rebateAmount) {
        this.rebateAmount = rebateAmount;
    }

    public void setSurcharge(BigDecimal surcharge) {
        this.surcharge = surcharge;
    }

    public void setCess(BigDecimal cess) {
        this.cess = cess;
    }

    public void setTaxPayable(BigDecimal taxPayable) {
        this.taxPayable = taxPayable;
    }

    public void setTaxPerMonth(BigDecimal taxPerMonth) {
        this.taxPerMonth = taxPerMonth;
    }

    public void setRemainingMonths(Integer remainingMonths) {
        this.remainingMonths = remainingMonths;
    }
}
