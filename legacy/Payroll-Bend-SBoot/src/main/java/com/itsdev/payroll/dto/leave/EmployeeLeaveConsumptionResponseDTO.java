package com.itsdev.payroll.dto.leave;

import java.time.LocalDateTime;
import java.util.List;

public class EmployeeLeaveConsumptionResponseDTO {

    private String employeeId;
    private String employeeNumber;
    private String employeeName;
    private String year;
    private Integer totalAllocatedDays = 0;
    private Integer totalConsumedDays = 0;
    private Integer totalRemainingDays = 0;
    private Integer totalLopDays = 0;
    private String leaveMonth;
    private Integer totalLwp = 0;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<LeaveAllocationItemDTO> leaveTypes;

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public Integer getTotalAllocatedDays() {
        return totalAllocatedDays;
    }

    public void setTotalAllocatedDays(Integer totalAllocatedDays) {
        this.totalAllocatedDays = totalAllocatedDays;
    }

    public Integer getTotalConsumedDays() {
        return totalConsumedDays;
    }

    public void setTotalConsumedDays(Integer totalConsumedDays) {
        this.totalConsumedDays = totalConsumedDays;
    }

    public Integer getTotalRemainingDays() {
        return totalRemainingDays;
    }

    public void setTotalRemainingDays(Integer totalRemainingDays) {
        this.totalRemainingDays = totalRemainingDays;
    }

    public Integer getTotalLopDays() {
        return totalLopDays != null ? totalLopDays : 0;
    }

    public void setTotalLopDays(Integer totalLopDays) {
        this.totalLopDays = totalLopDays != null ? totalLopDays : 0;
    }

    public String getLeaveMonth() {
        return leaveMonth;
    }

    public void setLeaveMonth(String leaveMonth) {
        this.leaveMonth = leaveMonth;
    }

    public Integer getTotalLwp() {
        return totalLwp != null ? totalLwp : 0;
    }

    public void setTotalLwp(Integer totalLwp) {
        this.totalLwp = totalLwp != null ? totalLwp : 0;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<LeaveAllocationItemDTO> getLeaveTypes() {
        return leaveTypes;
    }

    public void setLeaveTypes(List<LeaveAllocationItemDTO> leaveTypes) {
        this.leaveTypes = leaveTypes;
    }
}