package com.itsdev.payroll.dto.employee.SalaryRevision;

import java.time.LocalDate;
import java.util.List;

public class ProcessLaterRevisionDTO {

    private List<Long> revisionIds;   // multiple revise rows
    private LocalDate effectiveDate;  // new effective date from UI

    public List<Long> getRevisionIds() {
        return revisionIds;
    }

    public void setRevisionIds(List<Long> revisionIds) {
        this.revisionIds = revisionIds;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }
}

