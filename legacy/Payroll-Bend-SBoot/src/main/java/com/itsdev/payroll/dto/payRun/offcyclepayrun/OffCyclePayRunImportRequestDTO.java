package com.itsdev.payroll.dto.payRun.offcyclepayrun;


import java.util.List;

public class OffCyclePayRunImportRequestDTO {
    private String payrollRunId;  // existing run id to attach employees
    private List<OffCyclePayRunImportDTO> employees;

    // getters & setters


    public String getPayrollRunId() {
        return payrollRunId;
    }

    public void setPayrollRunId(String payrollRunId) {
        this.payrollRunId = payrollRunId;
    }

    public List<OffCyclePayRunImportDTO> getEmployees() {
        return employees;
    }

    public void setEmployees(List<OffCyclePayRunImportDTO> employees) {
        this.employees = employees;
    }
}
