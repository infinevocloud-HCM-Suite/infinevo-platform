package com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator;

import java.math.BigDecimal;

public class PreviousEmploymentSummary {

    private BigDecimal netPreviousIncome = BigDecimal.ZERO;
    private BigDecimal previousEmployerTds = BigDecimal.ZERO;

    public BigDecimal getNetPreviousIncome() {
        return netPreviousIncome;
    }

    public void setNetPreviousIncome(BigDecimal netPreviousIncome) {
        this.netPreviousIncome = netPreviousIncome;
    }

    public BigDecimal getPreviousEmployerTds() {
        return previousEmployerTds;
    }

    public void setPreviousEmployerTds(BigDecimal previousEmployerTds) {
        this.previousEmployerTds = previousEmployerTds;
    }
}

