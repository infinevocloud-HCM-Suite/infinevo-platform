package com.itsdev.payroll.entity.leave;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "employee_leave_balance_consumption"
)
public class EmployeeLeaveBalanceConsumption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "leave_id", unique = true, length = 100)
    private String leaveId;

    @Column(name = "allocation_id")
    private Long allocationId;

    @Column(name = "organization_id", nullable = false)
    private String organizationId;

    @Column(name = "employee_id", nullable = false)
    private String employeeId;

    @Column(name = "leave_type", nullable = false)
    private String leaveType;

    @Column(name = "year", length = 30)
    private String year;

    @Column(name = "consumed_days", nullable = false)
    private Integer consumedDays = 0;

    @Column(name = "balance_days", nullable = false)
    private Integer balanceDays = 0;

    @Column(name = "balance_after")
    private Integer balanceAfter;

    @Column(name = "running_ytd")
    private Integer runningYtd;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "leave_month", length = 50)
    private String leaveMonth;

    @Column(name = "monthly_breakdown", columnDefinition = "json")
    private String monthlyBreakdown;

    @Column(name = "monthly_lop_breakdown", columnDefinition = "json")
    private String monthlyLopBreakdown;

    @Column(name = "monthly_lwp_breakdown", columnDefinition = "json")
    private String monthlyLwpBreakdown;

    @Column(name = "monthly_entries", columnDefinition = "json")
    private String monthlyEntries;

    @Column(name = "lwp")
    private Integer lwp = 0;

    @Column(name = "lop_days")
    private Integer lopDays = 0;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLeaveId() {
        return leaveId;
    }

    public void setLeaveId(String leaveId) {
        this.leaveId = leaveId;
    }

    public Long getAllocationId() {
        return allocationId;
    }

    public void setAllocationId(Long allocationId) {
        this.allocationId = allocationId;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(String leaveType) {
        this.leaveType = leaveType;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public Integer getConsumedDays() {
        return consumedDays != null ? consumedDays : 0;
    }

    public void setConsumedDays(Integer consumedDays) {
        this.consumedDays = consumedDays != null ? consumedDays : 0;
    }

    public Integer getBalanceDays() {
        return balanceDays != null ? balanceDays : 0;
    }

    public void setBalanceDays(Integer balanceDays) {
        this.balanceDays = balanceDays != null ? balanceDays : 0;
    }

    public Integer getBalanceAfter() {
        return balanceAfter != null ? balanceAfter : balanceDays;
    }

    public void setBalanceAfter(Integer balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public Integer getRunningYtd() {
        return runningYtd != null ? runningYtd : consumedDays;
    }

    public void setRunningYtd(Integer runningYtd) {
        this.runningYtd = runningYtd;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getLeaveMonth() {
        return leaveMonth;
    }

    public void setLeaveMonth(String leaveMonth) {
        this.leaveMonth = leaveMonth;
    }

    public String getMonthlyBreakdown() {
        return monthlyBreakdown;
    }

    public void setMonthlyBreakdown(String monthlyBreakdown) {
        this.monthlyBreakdown = monthlyBreakdown;
    }

    public String getMonthlyLopBreakdown() {
        return monthlyLopBreakdown;
    }

    public void setMonthlyLopBreakdown(String monthlyLopBreakdown) {
        this.monthlyLopBreakdown = monthlyLopBreakdown;
    }

    public String getMonthlyEntries() {
        return monthlyEntries;
    }

    public void setMonthlyEntries(String monthlyEntries) {
        this.monthlyEntries = monthlyEntries;
    }

    public String getMonthlyLwpBreakdown() {
        return monthlyLwpBreakdown;
    }

    public void setMonthlyLwpBreakdown(String monthlyLwpBreakdown) {
        this.monthlyLwpBreakdown = monthlyLwpBreakdown;
    }

    public Integer getLwp() {
        return lwp != null ? lwp : 0;
    }

    public void setLwp(Integer lwp) {
        this.lwp = lwp != null ? lwp : 0;
    }

    public Integer getLopDays() {
        return lopDays != null ? lopDays : 0;
    }

    public void setLopDays(Integer lopDays) {
        this.lopDays = lopDays != null ? lopDays : 0;
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
}
