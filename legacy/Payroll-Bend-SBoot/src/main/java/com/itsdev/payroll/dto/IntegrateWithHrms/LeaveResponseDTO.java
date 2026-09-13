package com.itsdev.payroll.dto.IntegrateWithHrms;

public class LeaveResponseDTO {

    private String employeeEmail;
    private Double totalLeaves;

    public String getEmployeeEmail() {
        return employeeEmail;
    }

    public void setEmployeeEmail(String employeeEmail) {
        this.employeeEmail = employeeEmail;
    }

    public Double getTotalLeaves() {
        return totalLeaves;
    }

    public void setTotalLeaves(Double totalLeaves) {
        this.totalLeaves = totalLeaves;
    }
}
