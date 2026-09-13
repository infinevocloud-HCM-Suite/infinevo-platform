package com.itsdev.payroll.dto.leave;

public class ImportRowErrorDTO {
    private int rowNumber;
    private String employeeId;
    private String year;
    private String leaveType;
    private String errorMessage;

    public ImportRowErrorDTO() {}

    public ImportRowErrorDTO(int rowNumber, String employeeId, String year, String leaveType, String errorMessage) {
        this.rowNumber = rowNumber;
        this.employeeId = employeeId;
        this.year = year;
        this.leaveType = leaveType;
        this.errorMessage = errorMessage;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(int rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(String leaveType) {
        this.leaveType = leaveType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
