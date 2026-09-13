package com.itsdev.payroll.dto.payRun.offcyclepayrun;

import java.util.List;

public class OffCyclePayRunReleaseWithheldImportRequestDTO {
    private String payrollRunId;
    private List<OffCyclePayRunReleaseWithheldImportDTO> employees;

    // getters & setters


    public String getPayrollRunId() {
        return payrollRunId;
    }

    public void setPayrollRunId(String payrollRunId) {
        this.payrollRunId = payrollRunId;
    }

    public List<OffCyclePayRunReleaseWithheldImportDTO> getEmployees() {
        return employees;
    }

    public void setEmployees(List<OffCyclePayRunReleaseWithheldImportDTO> employees) {
        this.employees = employees;
    }
}

