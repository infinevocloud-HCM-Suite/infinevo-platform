package com.itsdev.payroll.dto.leave;

public class LeaveEntryDTO {
    private String entryId;
    private String leaveId;
    private String leaveType;
    private Integer daysTaken = 0;
    private Integer balanceAfter;
    private Integer runningYtd;
    private String leaveMonth;
    private String fromDate;
    private String toDate;
    private String reason;
    private Integer lopDays = 0;
    private Integer lwp = 0;
    private String markedBy;
    private String markedAt;

    public LeaveEntryDTO() {}

    public LeaveEntryDTO(String entryId, String leaveType, Integer daysTaken, String fromDate, String toDate, String reason, Integer lopDays, Integer lwp, String markedBy, String markedAt) {
        this.entryId = entryId;
        this.leaveId = entryId;
        this.leaveType = leaveType;
        this.daysTaken = daysTaken;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.reason = reason;
        this.lopDays = lopDays;
        this.lwp = lwp;
        this.markedBy = markedBy;
        this.markedAt = markedAt;
    }

    public String getEntryId() {
        return (entryId != null && !entryId.isBlank()) ? entryId : leaveId;
    }

    public void setEntryId(String entryId) {
        this.entryId = entryId;
        if (this.leaveId == null) this.leaveId = entryId;
    }

    public String getLeaveId() {
        return (leaveId != null && !leaveId.isBlank()) ? leaveId : entryId;
    }

    public void setLeaveId(String leaveId) {
        this.leaveId = leaveId;
        if (this.entryId == null) this.entryId = leaveId;
    }

    public String getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(String leaveType) {
        this.leaveType = leaveType;
    }

    public Integer getDaysTaken() {
        return daysTaken != null ? daysTaken : 0;
    }

    public void setDaysTaken(Integer daysTaken) {
        this.daysTaken = daysTaken != null ? daysTaken : 0;
    }

    public Integer getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(Integer balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public Integer getRunningYtd() {
        return runningYtd;
    }

    public void setRunningYtd(Integer runningYtd) {
        this.runningYtd = runningYtd;
    }

    public String getLeaveMonth() {
        return leaveMonth;
    }

    public void setLeaveMonth(String leaveMonth) {
        this.leaveMonth = leaveMonth;
    }

    public String getFromDate() {
        return fromDate;
    }

    public void setFromDate(String fromDate) {
        this.fromDate = fromDate;
    }

    public String getToDate() {
        return toDate;
    }

    public void setToDate(String toDate) {
        this.toDate = toDate;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getLopDays() {
        return lopDays != null ? lopDays : 0;
    }

    public void setLopDays(Integer lopDays) {
        this.lopDays = lopDays != null ? lopDays : 0;
    }

    public Integer getLwp() {
        return lwp != null ? lwp : 0;
    }

    public void setLwp(Integer lwp) {
        this.lwp = lwp != null ? lwp : 0;
    }

    public String getMarkedBy() {
        return markedBy;
    }

    public void setMarkedBy(String markedBy) {
        this.markedBy = markedBy;
    }

    public String getMarkedAt() {
        return markedAt;
    }

    public void setMarkedAt(String markedAt) {
        this.markedAt = markedAt;
    }
}
