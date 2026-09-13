package com.itsdev.payroll.dto.leaveAndAttendance.preferences;


import java.util.UUID;

public class PreferencesDTO {

    private UUID id;
    private String endDay;
    private String payrollReportDay;
    private Boolean leaveEncashmentEnabled;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEndDay() {
        return endDay;
    }

    public void setEndDay(String endDay) {
        this.endDay = endDay;
    }

    public String getPayrollReportDay() {
        return payrollReportDay;
    }

    public void setPayrollReportDay(String payrollReportDay) {
        this.payrollReportDay = payrollReportDay;
    }

    public Boolean getLeaveEncashmentEnabled() {
        return leaveEncashmentEnabled;
    }

    public void setLeaveEncashmentEnabled(Boolean leaveEncashmentEnabled) {
        this.leaveEncashmentEnabled = leaveEncashmentEnabled;


    }
}

