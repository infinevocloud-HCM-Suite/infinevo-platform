package com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator;


import com.itsdev.payroll.entity.employee.CtcStructure;

import java.time.LocalDate;

public class CtcPeriod {

    private CtcStructure ctc;
    private LocalDate startDate;
    private LocalDate endDate;
    private int months;

    // getters & setters


    public CtcStructure getCtc() {
        return ctc;
    }

    public void setCtc(CtcStructure ctc) {
        this.ctc = ctc;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public int getMonths() {
        return months;
    }

    public void setMonths(int months) {
        this.months = months;
    }
}

