package com.itsdev.payroll.dto.payRun.oneTimePayout;


import java.math.BigDecimal;

public class OneTimePayoutImportDTO {
    private String employeeNumber; // from CSV
    private BigDecimal earningAmount;
    private String incomeTax;
    private String incomeTaxOverrideReason;

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public BigDecimal getEarningAmount() {
        return earningAmount;
    }

    public void setEarningAmount(BigDecimal earningAmount) {
        this.earningAmount = earningAmount;
    }

    public String getIncomeTax() {
        return incomeTax;
    }

    public void setIncomeTax(String incomeTax) {
        this.incomeTax = incomeTax;
    }

    public String getIncomeTaxOverrideReason() {
        return incomeTaxOverrideReason;
    }

    public void setIncomeTaxOverrideReason(String incomeTaxOverrideReason) {
        this.incomeTaxOverrideReason = incomeTaxOverrideReason;
    }
}

