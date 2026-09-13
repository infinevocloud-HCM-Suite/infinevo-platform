package com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator;

import java.math.BigDecimal;

public class NewTaxCalculationResult {

    /* ==========================
       INCOME
       ========================== */
    private BigDecimal grossIncome;
    private BigDecimal incomeFromSalary;
    // private BigDecimal incomeFromOtherSources;

    /* ==========================
       DEDUCTIONS
       ========================== */
    private BigDecimal standardDeduction;

    /* ==========================
       TAX CALCULATION
       ========================== */
    private BigDecimal taxableIncome;
    private BigDecimal taxBeforeRebate;
    private BigDecimal rebateAmount;
    private BigDecimal surcharge;
    private BigDecimal cess;
    private BigDecimal taxPayable;

    /* ==========================
       PAYROLL
       ========================== */
    private BigDecimal taxPerMonth;
    private Integer remainingMonths;

    /* ==========================
       GETTERS & SETTERS
       ========================== */

    public BigDecimal getGrossIncome() { return grossIncome; }
    public void setGrossIncome(BigDecimal grossIncome) { this.grossIncome = grossIncome; }

    public BigDecimal getIncomeFromSalary() { return incomeFromSalary; }
    public void setIncomeFromSalary(BigDecimal incomeFromSalary) { this.incomeFromSalary = incomeFromSalary; }

    // public BigDecimal getIncomeFromOtherSources() { return incomeFromOtherSources; }
    // public void setIncomeFromOtherSources(BigDecimal incomeFromOtherSources) {
    //     this.incomeFromOtherSources = incomeFromOtherSources;
    // }

    public BigDecimal getStandardDeduction() { return standardDeduction; }
    public void setStandardDeduction(BigDecimal standardDeduction) {
        this.standardDeduction = standardDeduction;
    }

    public BigDecimal getTaxableIncome() { return taxableIncome; }
    public void setTaxableIncome(BigDecimal taxableIncome) {
        this.taxableIncome = taxableIncome;
    }

    public BigDecimal getTaxBeforeRebate() { return taxBeforeRebate; }
    public void setTaxBeforeRebate(BigDecimal taxBeforeRebate) {
        this.taxBeforeRebate = taxBeforeRebate;
    }

    public BigDecimal getRebateAmount() { return rebateAmount; }
    public void setRebateAmount(BigDecimal rebateAmount) {
        this.rebateAmount = rebateAmount;
    }

    public BigDecimal getSurcharge() { return surcharge; }
    public void setSurcharge(BigDecimal surcharge) {
        this.surcharge = surcharge;
    }

    public BigDecimal getCess() { return cess; }
    public void setCess(BigDecimal cess) {
        this.cess = cess;
    }

    public BigDecimal getTaxPayable() { return taxPayable; }
    public void setTaxPayable(BigDecimal taxPayable) {
        this.taxPayable = taxPayable;
    }

    public BigDecimal getTaxPerMonth() { return taxPerMonth; }
    public void setTaxPerMonth(BigDecimal taxPerMonth) {
        this.taxPerMonth = taxPerMonth;
    }

    public Integer getRemainingMonths() { return remainingMonths; }
    public void setRemainingMonths(Integer remainingMonths) {
        this.remainingMonths = remainingMonths;
    }
}
