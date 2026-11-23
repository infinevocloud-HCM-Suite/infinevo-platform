package com.infinevo.payroll.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing an aggregated Full and Final (F&F) Settlement for an exiting employee (W-90.3).
 * Maps to {@code payroll.fnf_settlement}.
 */
@Entity
@Table(name = "fnf_settlement", schema = "payroll")
public class FnfSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "resignation_date", nullable = false)
    private LocalDate resignationDate;

    @Column(name = "exit_date", nullable = false)
    private LocalDate exitDate;

    @Column(name = "exit_reason", nullable = false, length = 50)
    private String exitReason;

    @Column(name = "working_days_in_exit_month", nullable = false)
    private int workingDaysInExitMonth;

    @Column(name = "exit_month_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal exitMonthSalary;

    @Column(name = "leave_encashment_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal leaveEncashmentAmount;

    @Column(name = "gratuity_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal gratuityAmount;

    @Column(name = "notice_pay_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal noticePayAmount;

    @Column(name = "other_earnings", nullable = false, precision = 14, scale = 2)
    private BigDecimal otherEarnings;

    @Column(name = "gross_earnings", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossEarnings;

    @Column(name = "notice_recovery_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal noticeRecoveryAmount;

    @Column(name = "epf_deduction", nullable = false, precision = 14, scale = 2)
    private BigDecimal epfDeduction;

    @Column(name = "esi_deduction", nullable = false, precision = 14, scale = 2)
    private BigDecimal esiDeduction;

    @Column(name = "pt_deduction", nullable = false, precision = 14, scale = 2)
    private BigDecimal ptDeduction;

    @Column(name = "tds_deduction", nullable = false, precision = 14, scale = 2)
    private BigDecimal tdsDeduction;

    @Column(name = "other_recoveries", nullable = false, precision = 14, scale = 2)
    private BigDecimal otherRecoveries;

    @Column(name = "gross_deductions", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossDeductions;

    @Column(name = "net_payable", nullable = false, precision = 14, scale = 2)
    private BigDecimal netPayable;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy = "system";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy = "system";

    public FnfSettlement() {}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public LocalDate getResignationDate() {
        return resignationDate;
    }

    public void setResignationDate(LocalDate resignationDate) {
        this.resignationDate = resignationDate;
    }

    public LocalDate getExitDate() {
        return exitDate;
    }

    public void setExitDate(LocalDate exitDate) {
        this.exitDate = exitDate;
    }

    public String getExitReason() {
        return exitReason;
    }

    public void setExitReason(String exitReason) {
        this.exitReason = exitReason;
    }

    public int getWorkingDaysInExitMonth() {
        return workingDaysInExitMonth;
    }

    public void setWorkingDaysInExitMonth(int workingDaysInExitMonth) {
        this.workingDaysInExitMonth = workingDaysInExitMonth;
    }

    public BigDecimal getExitMonthSalary() {
        return exitMonthSalary;
    }

    public void setExitMonthSalary(BigDecimal exitMonthSalary) {
        this.exitMonthSalary = exitMonthSalary;
    }

    public BigDecimal getLeaveEncashmentAmount() {
        return leaveEncashmentAmount;
    }

    public void setLeaveEncashmentAmount(BigDecimal leaveEncashmentAmount) {
        this.leaveEncashmentAmount = leaveEncashmentAmount;
    }

    public BigDecimal getGratuityAmount() {
        return gratuityAmount;
    }

    public void setGratuityAmount(BigDecimal gratuityAmount) {
        this.gratuityAmount = gratuityAmount;
    }

    public BigDecimal getNoticePayAmount() {
        return noticePayAmount;
    }

    public void setNoticePayAmount(BigDecimal noticePayAmount) {
        this.noticePayAmount = noticePayAmount;
    }

    public BigDecimal getOtherEarnings() {
        return otherEarnings;
    }

    public void setOtherEarnings(BigDecimal otherEarnings) {
        this.otherEarnings = otherEarnings;
    }

    public BigDecimal getGrossEarnings() {
        return grossEarnings;
    }

    public void setGrossEarnings(BigDecimal grossEarnings) {
        this.grossEarnings = grossEarnings;
    }

    public BigDecimal getNoticeRecoveryAmount() {
        return noticeRecoveryAmount;
    }

    public void setNoticeRecoveryAmount(BigDecimal noticeRecoveryAmount) {
        this.noticeRecoveryAmount = noticeRecoveryAmount;
    }

    public BigDecimal getEpfDeduction() {
        return epfDeduction;
    }

    public void setEpfDeduction(BigDecimal epfDeduction) {
        this.epfDeduction = epfDeduction;
    }

    public BigDecimal getEsiDeduction() {
        return esiDeduction;
    }

    public void setEsiDeduction(BigDecimal esiDeduction) {
        this.esiDeduction = esiDeduction;
    }

    public BigDecimal getPtDeduction() {
        return ptDeduction;
    }

    public void setPtDeduction(BigDecimal ptDeduction) {
        this.ptDeduction = ptDeduction;
    }

    public BigDecimal getTdsDeduction() {
        return tdsDeduction;
    }

    public void setTdsDeduction(BigDecimal tdsDeduction) {
        this.tdsDeduction = tdsDeduction;
    }

    public BigDecimal getOtherRecoveries() {
        return otherRecoveries;
    }

    public void setOtherRecoveries(BigDecimal otherRecoveries) {
        this.otherRecoveries = otherRecoveries;
    }

    public BigDecimal getGrossDeductions() {
        return grossDeductions;
    }

    public void setGrossDeductions(BigDecimal grossDeductions) {
        this.grossDeductions = grossDeductions;
    }

    public BigDecimal getNetPayable() {
        return netPayable;
    }

    public void setNetPayable(BigDecimal netPayable) {
        this.netPayable = netPayable;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Instant paidAt) {
        this.paidAt = paidAt;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FnfSettlement that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
