package com.itsdev.payroll.dto.leave;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public class LeaveAllocationItemDTO {

    private Long id;

    @NotBlank(message = "Leave type is required")
    private String leaveType;

    private Integer annualDays;

    private Integer remainingDays;

    private Integer carriedForwardDays;

    private Integer consumedDays;

    private Integer totalDays;

    private Integer balanceDays;

    private Integer lopDays;

    private java.util.Map<String, Integer> monthlyLopBreakdown;
    private java.util.Map<String, Integer> monthlyLwpBreakdown;

    private java.util.Map<String, Integer> monthlyBreakdown;
    private java.util.Map<String, java.util.List<LeaveEntryDTO>> monthlyEntries;

    private String leaveMonth;

    private Integer lwp = 0;

    private LocalDate expirationDate;

    private Boolean carryForward = false;
    private String reason;
    private Integer daysTaken;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(String leaveType) {
        this.leaveType = leaveType;
    }

    public Integer getAnnualDays() {
        return annualDays;
    }

    public void setAnnualDays(Integer annualDays) {
        this.annualDays = annualDays;
    }

    public Integer getRemainingDays() {
        return remainingDays;
    }

    public void setRemainingDays(Integer remainingDays) {
        this.remainingDays = remainingDays;
    }

    public Integer getCarriedForwardDays() {
        return carriedForwardDays != null ? carriedForwardDays : 0;
    }

    public void setCarriedForwardDays(Integer carriedForwardDays) {
        this.carriedForwardDays = carriedForwardDays;
    }

    public Integer getConsumedDays() {
        return consumedDays;
    }

    public void setConsumedDays(Integer consumedDays) {
        this.consumedDays = consumedDays;
    }

    public Integer getTotalDays() {
        int annual = annualDays != null ? annualDays : 0;
        int carried = carriedForwardDays != null ? carriedForwardDays : 0;
        return annual + carried;
    }

    public void setTotalDays(Integer totalDays) {
        this.totalDays = totalDays;
    }

    public Integer getBalanceDays() {
        int total = getTotalDays();
        int consumed = consumedDays != null ? consumedDays : 0;
        return Math.max(0, total - consumed);
    }

    public void setBalanceDays(Integer balanceDays) {
        this.balanceDays = balanceDays;
    }

    public Integer getLopDays() {
        if (lopDays != null && lopDays > 0) {
            return lopDays;
        }
        int total = getTotalDays();
        int consumed = consumedDays != null ? consumedDays : 0;
        return consumed > total ? (consumed - total) : 0;
    }

    public void setLopDays(Integer lopDays) {
        this.lopDays = lopDays;
    }

    public java.util.Map<String, Integer> getMonthlyLopBreakdown() {
        return monthlyLopBreakdown;
    }

    public void setMonthlyLopBreakdown(java.util.Map<String, Integer> monthlyLopBreakdown) {
        this.monthlyLopBreakdown = monthlyLopBreakdown;
    }

    public java.util.Map<String, Integer> getMonthlyLwpBreakdown() {
        return monthlyLwpBreakdown;
    }

    public void setMonthlyLwpBreakdown(java.util.Map<String, Integer> monthlyLwpBreakdown) {
        this.monthlyLwpBreakdown = monthlyLwpBreakdown;
    }

    public java.util.Map<String, java.util.List<LeaveEntryDTO>> getMonthlyEntries() {
        return monthlyEntries;
    }

    public void setMonthlyEntries(java.util.Map<String, java.util.List<LeaveEntryDTO>> monthlyEntries) {
        this.monthlyEntries = monthlyEntries;
    }

    public java.util.Map<String, Integer> getMonthlyBreakdown() {
        return monthlyBreakdown;
    }

    public void setMonthlyBreakdown(java.util.Map<String, Integer> monthlyBreakdown) {
        this.monthlyBreakdown = monthlyBreakdown;
    }

    public String getLeaveMonth() {
        return leaveMonth;
    }

    public void setLeaveMonth(String leaveMonth) {
        this.leaveMonth = leaveMonth;
    }

    public Integer getLwp() {
        return lwp != null ? lwp : 0;
    }

    public void setLwp(Integer lwp) {
        this.lwp = lwp;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(LocalDate expirationDate) {
        this.expirationDate = expirationDate;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getDaysTaken() {
        return daysTaken != null ? daysTaken : consumedDays;
    }

    public void setDaysTaken(Integer daysTaken) {
        this.daysTaken = daysTaken;
        if (this.consumedDays == null) this.consumedDays = daysTaken;
    }

    public Boolean getCarryForward() {
        return carryForward;
    }

    public void setCarryForward(Boolean carryForward) {
        this.carryForward = carryForward;
    }
}
