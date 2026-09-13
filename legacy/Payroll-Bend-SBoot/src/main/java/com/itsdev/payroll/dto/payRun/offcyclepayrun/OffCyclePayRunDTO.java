package com.itsdev.payroll.dto.payRun.offcyclepayrun;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class OffCyclePayRunDTO {

    private String payrollRunId;
    private LocalDate payDate;
    private String status;
    private String statusFormatted;
    private String type;
    private String notes;

    private List<OffCyclePayrunEmployeeDTO> employees = new ArrayList<>();

    // Getters & Setters
    public String getPayrollRunId() {
        return payrollRunId;
    }

    public void setPayrollRunId(String payrollRunId) {
        this.payrollRunId = payrollRunId;
    }

    public LocalDate getPayDate() {
        return payDate;
    }

    public void setPayDate(LocalDate payDate) {
        this.payDate = payDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusFormatted() {
        return statusFormatted;
    }

    public void setStatusFormatted(String statusFormatted) {
        this.statusFormatted = statusFormatted;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<OffCyclePayrunEmployeeDTO> getEmployees() {
        return employees;
    }

    public void setEmployees(List<OffCyclePayrunEmployeeDTO> employees) {
        this.employees = employees;
    }
}

