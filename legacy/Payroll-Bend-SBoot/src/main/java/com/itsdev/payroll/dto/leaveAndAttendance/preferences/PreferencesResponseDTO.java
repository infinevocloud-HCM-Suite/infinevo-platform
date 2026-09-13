package com.itsdev.payroll.dto.leaveAndAttendance.preferences;

public class PreferencesResponseDTO {
    private String endDay;                       // from Preferences
    private String payrollReportDay;             // from Preferences
    private Boolean leaveEncashmentEnabled;      // from Preferences

    // From PaySchedule
    private String payScheduleType;              // available
    private String payScheduleTypeFormatted;     // derived from payScheduleType
    private Boolean payScheduleConfigured;       // default true if found

    // From LeaveType
    private Boolean leaveEncashmentSupported;    // available

    // Not yet available → set defaults
    private Boolean lopSettingsConfigured = false;
    private String lopDay = null;
    private Boolean overtimeAllowanceSupported = false;
    private Boolean overtimeAllowanceEnabled = false;

    // Getters & Setters

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

    public String getPayScheduleType() {
        return payScheduleType;
    }

    public void setPayScheduleType(String payScheduleType) {
        this.payScheduleType = payScheduleType;
    }

    public String getPayScheduleTypeFormatted() {
        return payScheduleTypeFormatted;
    }

    public void setPayScheduleTypeFormatted(String payScheduleTypeFormatted) {
        this.payScheduleTypeFormatted = payScheduleTypeFormatted;
    }

    public Boolean getPayScheduleConfigured() {
        return payScheduleConfigured;
    }

    public void setPayScheduleConfigured(Boolean payScheduleConfigured) {
        this.payScheduleConfigured = payScheduleConfigured;
    }

    public Boolean getLeaveEncashmentSupported() {
        return leaveEncashmentSupported;
    }

    public void setLeaveEncashmentSupported(Boolean leaveEncashmentSupported) {
        this.leaveEncashmentSupported = leaveEncashmentSupported;
    }

    public Boolean getLopSettingsConfigured() {
        return lopSettingsConfigured;
    }

    public void setLopSettingsConfigured(Boolean lopSettingsConfigured) {
        this.lopSettingsConfigured = lopSettingsConfigured;
    }

    public String getLopDay() {
        return lopDay;
    }

    public void setLopDay(String lopDay) {
        this.lopDay = lopDay;
    }

    public Boolean getOvertimeAllowanceSupported() {
        return overtimeAllowanceSupported;
    }

    public void setOvertimeAllowanceSupported(Boolean overtimeAllowanceSupported) {
        this.overtimeAllowanceSupported = overtimeAllowanceSupported;
    }

    public Boolean getOvertimeAllowanceEnabled() {
        return overtimeAllowanceEnabled;
    }

    public void setOvertimeAllowanceEnabled(Boolean overtimeAllowanceEnabled) {
        this.overtimeAllowanceEnabled = overtimeAllowanceEnabled;
    }
}

