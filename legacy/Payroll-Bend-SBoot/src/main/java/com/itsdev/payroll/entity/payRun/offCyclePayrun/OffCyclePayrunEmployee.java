package com.itsdev.payroll.entity.payRun.offCyclePayrun;

import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.organization.Organization;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "off_cycle_payrun_employee")
public class OffCyclePayrunEmployee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Link to parent OffCyclePayRun
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_run_id", nullable = false)
    private OffCyclePayRun payrollRun;

    // Employee reference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private BasicDetails employee;

    // Organization reference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    // Earnings → mapped list
    @OneToMany(mappedBy = "offCyclePayrunEmployee", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OffCyclePayrunEmployeeEarning> earnings = new ArrayList<>();

    // Deductions → mapped list
    @OneToMany(mappedBy = "offCyclePayrunEmployee", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OffCyclePayrunEmployeeDeduction> deductions = new ArrayList<>();

    // JSON fields
    @Column(columnDefinition = "json")
    private String lopAdjustmentDetails;

    @Column(columnDefinition = "json")
    private String taxes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // getters/setters...


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public OffCyclePayRun getPayrollRun() {
        return payrollRun;
    }

    public void setPayrollRun(OffCyclePayRun payrollRun) {
        this.payrollRun = payrollRun;
    }

    public BasicDetails getEmployee() {
        return employee;
    }

    public void setEmployee(BasicDetails employee) {
        this.employee = employee;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public List<OffCyclePayrunEmployeeEarning> getEarnings() {
        return earnings;
    }

    public void setEarnings(List<OffCyclePayrunEmployeeEarning> earnings) {
        this.earnings = earnings;
    }

    public List<OffCyclePayrunEmployeeDeduction> getDeductions() {
        return deductions;
    }

    public void setDeductions(List<OffCyclePayrunEmployeeDeduction> deductions) {
        this.deductions = deductions;
    }

    public String getLopAdjustmentDetails() {
        return lopAdjustmentDetails;
    }

    public void setLopAdjustmentDetails(String lopAdjustmentDetails) {
        this.lopAdjustmentDetails = lopAdjustmentDetails;
    }

    public String getTaxes() {
        return taxes;
    }

    public void setTaxes(String taxes) {
        this.taxes = taxes;
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
