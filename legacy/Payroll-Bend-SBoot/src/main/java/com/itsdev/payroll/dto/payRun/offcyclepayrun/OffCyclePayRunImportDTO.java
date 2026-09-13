package com.itsdev.payroll.dto.payRun.offcyclepayrun;

import java.math.BigDecimal;

public class OffCyclePayRunImportDTO {
    private String employeeNumber;
    private BigDecimal bonus;
    private BigDecimal commission;
    private BigDecimal incomeTax;
    private String incomeTaxOverrideReason;

    // getters & setters


    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public BigDecimal getBonus() {
        return bonus;
    }

    public void setBonus(BigDecimal bonus) {
        this.bonus = bonus;
    }

    public BigDecimal getCommission() {
        return commission;
    }

    public void setCommission(BigDecimal commission) {
        this.commission = commission;
    }

    public BigDecimal getIncomeTax() {
        return incomeTax;
    }

    public void setIncomeTax(BigDecimal incomeTax) {
        this.incomeTax = incomeTax;
    }

    public String getIncomeTaxOverrideReason() {
        return incomeTaxOverrideReason;
    }

    public void setIncomeTaxOverrideReason(String incomeTaxOverrideReason) {
        this.incomeTaxOverrideReason = incomeTaxOverrideReason;
    }
}

