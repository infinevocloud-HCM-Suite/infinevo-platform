package com.itsdev.payroll.dto.leaveAndAttendance.leaveImport;

import java.time.LocalDate;

public class EmployeeLeaveImportDTO {

    private Integer count;
    private LocalDate date;
    private String employeeNumber;
    private String leaveType;

    public Integer getCount() {
        return count;
    }
    public void setCount(Integer count) {
        this.count = count;
    }

    public LocalDate getDate() {
        return date;
    }
    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }
    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public String getLeaveType() {
        return leaveType;
    }
    public void setLeaveType(String leaveType) {
        this.leaveType = leaveType;
    }
}

