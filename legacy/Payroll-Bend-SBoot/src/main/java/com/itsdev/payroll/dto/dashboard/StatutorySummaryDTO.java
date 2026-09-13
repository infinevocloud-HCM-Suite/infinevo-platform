package com.itsdev.payroll.dto.dashboard;

import java.math.BigDecimal;

public class StatutorySummaryDTO {

    private String type;                       // "EPF" or "ESI"
    private BigDecimal employeeContribution;   // total employee share
    private BigDecimal employerContribution;   // total employer share
    private BigDecimal total;                  // employee + employer


    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BigDecimal getEmployeeContribution() {
        return employeeContribution;
    }

    public void setEmployeeContribution(BigDecimal employeeContribution) {
        this.employeeContribution = employeeContribution;
    }

    public BigDecimal getEmployerContribution() {
        return employerContribution;
    }

    public void setEmployerContribution(BigDecimal employerContribution) {
        this.employerContribution = employerContribution;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }
}

