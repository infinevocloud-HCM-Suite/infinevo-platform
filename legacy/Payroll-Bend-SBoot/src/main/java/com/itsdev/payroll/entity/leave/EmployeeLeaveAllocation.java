package com.itsdev.payroll.entity.leave;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "employee_leave_allocation",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_emp_leave_alloc",
            columnNames = {"organization_id", "employee_id", "leave_type", "year"}
        )
    }
)
public class EmployeeLeaveAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private String organizationId;

    @Column(name = "employee_id", nullable = false)
    private String employeeId;

    @Column(name = "leave_type", nullable = false)
    private String leaveType;

    @Column(name = "year", length = 30)
    private String year;

    @Column(name = "annual_days", nullable = false)
    private Integer annualDays;

    @Column(name = "carried_forward_days", nullable = false)
    private Integer carriedForwardDays = 0;

    @Column(name = "consumed_days", nullable = false)
    private Integer consumedDays = 0;

    @Column(name = "monthly_lwp_breakdown", columnDefinition = "json")
    private String monthlyLwpBreakdown;

    @Column(name = "monthly_lop_breakdown", columnDefinition = "json")
    private String monthlyLopBreakdown;

    @Column(name = "monthly_breakdown", columnDefinition = "json")
    private String monthlyBreakdown;

    @Column(name = "leave_month", length = 50)
    private String leaveMonth;

    @Column(name = "lwp")
    private Integer lwp = 0;

    @Column(name = "lop_days")
    private Integer lopDays = 0;

    @Column(name = "expiration_date", nullable = false)
    private LocalDate expirationDate;

    @Column(name = "carry_forward")
    private Boolean carryForward = false;

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

    public Integer getAnnualDays() {
        return annualDays;
    }

    public void setAnnualDays(Integer annualDays) {
        this.annualDays = annualDays;
    }

    public Integer getCarriedForwardDays() {
        return carriedForwardDays != null ? carriedForwardDays : 0;
    }

    public void setCarriedForwardDays(Integer carriedForwardDays) {
        this.carriedForwardDays = carriedForwardDays != null ? carriedForwardDays : 0;
    }

    public String getMonthlyLwpBreakdown() {
        return monthlyLwpBreakdown;
    }

    public void setMonthlyLwpBreakdown(String monthlyLwpBreakdown) {
        this.monthlyLwpBreakdown = monthlyLwpBreakdown;
    }

    public String getMonthlyLopBreakdown() {
        return monthlyLopBreakdown;
    }

    public void setMonthlyLopBreakdown(String monthlyLopBreakdown) {
        this.monthlyLopBreakdown = monthlyLopBreakdown;
    }

    public String getMonthlyBreakdown() {
        return monthlyBreakdown;
    }

    public void setMonthlyBreakdown(String monthlyBreakdown) {
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
        this.lwp = lwp != null ? lwp : 0;
    }

    public Integer getLopDays() {
        return lopDays != null ? lopDays : 0;
    }

    public void setLopDays(Integer lopDays) {
        this.lopDays = lopDays != null ? lopDays : 0;
    }

    public Integer getConsumedDays() {
        return consumedDays != null ? consumedDays : 0;
    }

    public void setConsumedDays(Integer consumedDays) {
        this.consumedDays = consumedDays != null ? consumedDays : 0;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(LocalDate expirationDate) {
        this.expirationDate = expirationDate;
    }

    public Boolean getCarryForward() {
        return carryForward != null ? carryForward : false;
    }

    public void setCarryForward(Boolean carryForward) {
        this.carryForward = carryForward != null ? carryForward : false;
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
