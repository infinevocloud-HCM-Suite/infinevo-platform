package com.itsdev.payroll.dto.payRun.offcyclepayrun;

public class OffCyclePayRunReleaseWithheldImportDTO {

    private String employeeNumber;   // CSV: Employee Number
    private String notes;            // CSV: Notes
    private Boolean releaseWithheldSalary;  // CSV: Release Withheld Salary (Y/N or true/false)
    private Integer monthsToRelease; // CSV: Months To Release Withheld Salary

    // getters & setters


    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Boolean getReleaseWithheldSalary() {
        return releaseWithheldSalary;
    }

    public void setReleaseWithheldSalary(Boolean releaseWithheldSalary) {
        this.releaseWithheldSalary = releaseWithheldSalary;
    }

    public Integer getMonthsToRelease() {
        return monthsToRelease;
    }

    public void setMonthsToRelease(Integer monthsToRelease) {
        this.monthsToRelease = monthsToRelease;
    }
}

