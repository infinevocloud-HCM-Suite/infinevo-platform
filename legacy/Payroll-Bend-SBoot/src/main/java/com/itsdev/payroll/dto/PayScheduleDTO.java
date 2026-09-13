package com.itsdev.payroll.dto;

import java.time.LocalDate;
import java.util.List;

public class PayScheduleDTO {
    private String payScheduleId;
    private String payScheduleType;
    private String payDay;
    private LocalDate payPeriodStartDate;
    private LocalDate payPeriodEndDate;
    private LocalDate payDate;
    private List<String> workingDays;
    private Integer noOfWorkingDays;
    private Boolean includeHolidays;
    private Boolean includeWeekends;
    private String workingDaysCalculationType;

    public String getPayScheduleId() {
        return payScheduleId;
    }
    public void setPayScheduleId(String payScheduleId) {
        this.payScheduleId = payScheduleId;
    }

    public String getPayScheduleType() {
        return payScheduleType;
    }
    public void setPayScheduleType(String payScheduleType) {
        this.payScheduleType = payScheduleType;
    }

    public String getPayDay() {
        return payDay;
    }
    public void setPayDay(String payDay) {
        this.payDay = payDay;
    }

    public LocalDate getPayPeriodStartDate() {
        return payPeriodStartDate;
    }
    public void setPayPeriodStartDate(LocalDate payPeriodStartDate) {
        this.payPeriodStartDate = payPeriodStartDate;
    }

    public LocalDate getPayPeriodEndDate() {
        return payPeriodEndDate;
    }
    public void setPayPeriodEndDate(LocalDate payPeriodEndDate) {
        this.payPeriodEndDate = payPeriodEndDate;
    }

    public LocalDate getPayDate() {
        return payDate;
    }
    public void setPayDate(LocalDate payDate) {
        this.payDate = payDate;
    }

    public List<String> getWorkingDays() {
        return workingDays;
    }
    public void setWorkingDays(List<String> workingDays) {
        this.workingDays = workingDays;
    }

    public Integer getNoOfWorkingDays() {
        return noOfWorkingDays;
    }
    public void setNoOfWorkingDays(Integer noOfWorkingDays) {
        this.noOfWorkingDays = noOfWorkingDays;
    }

    public Boolean getIncludeHolidays() {
        return includeHolidays;
    }
    public void setIncludeHolidays(Boolean includeHolidays) {
        this.includeHolidays = includeHolidays;
    }

    public Boolean getIncludeWeekends() {
        return includeWeekends;
    }
    public void setIncludeWeekends(Boolean includeWeekends) {
        this.includeWeekends = includeWeekends;
    }

    public String getWorkingDaysCalculationType() {
        return workingDaysCalculationType;
    }
    public void setWorkingDaysCalculationType(String workingDaysCalculationType) {
        this.workingDaysCalculationType = workingDaysCalculationType;
    }
}
