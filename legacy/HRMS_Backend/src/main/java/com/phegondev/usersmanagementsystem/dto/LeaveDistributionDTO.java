package com.phegondev.usersmanagementsystem.dto;

public class LeaveDistributionDTO {
    private String leaveTypeId;
    private String leaveTypeName;
    private Double allocatedDays;

    public LeaveDistributionDTO() {}

    public LeaveDistributionDTO(String leaveTypeId, String leaveTypeName, Double allocatedDays) {
        this.leaveTypeId = leaveTypeId;
        this.leaveTypeName = leaveTypeName;
        this.allocatedDays = allocatedDays;
    }

    // getters & setters
    public String getLeaveTypeId() { return leaveTypeId; }
    public void setLeaveTypeId(String leaveTypeId) { this.leaveTypeId = leaveTypeId; }
    public String getLeaveTypeName() { return leaveTypeName; }
    public void setLeaveTypeName(String leaveTypeName) { this.leaveTypeName = leaveTypeName; }
    public Double getAllocatedDays() { return allocatedDays; }
    public void setAllocatedDays(Double allocatedDays) { this.allocatedDays = allocatedDays; }
}
