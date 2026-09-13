package com.itsdev.payroll.dto.dashboard;


import java.math.BigDecimal;

public class TdsSummaryDTO {

    private BigDecimal totalContribution;  // total TDS deducted

    public BigDecimal getTotalContribution() {
        return totalContribution;
    }

    public void setTotalContribution(BigDecimal totalContribution) {
        this.totalContribution = totalContribution;
    }
}

