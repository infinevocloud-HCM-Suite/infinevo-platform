package com.itsdev.payroll.dto.payruns;

import com.itsdev.payroll.enumeration.payruns.PayRunStatus;
import com.itsdev.payroll.enumeration.payruns.PayRunType;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PayRunDTO {
    private String payrunId;
    private PayRunType type;
    private PayRunStatus status;
    private Boolean isPaymentDue;
    private String paymentStatus;
    private LocalDate payPeriodStartDate;
    private LocalDate payPeriodEndDate;
    private LocalDate payDate;
    private String processingPeriod;
    private BigDecimal payrollTotal;
    private String statusInfo;
    private Integer noOfEmployees;
    private String approvalType;
    private String approvalDetails;
    private String compensationName;
    private LocalDate approvedDate;

    private BigDecimal totalNetPay;
    private BigDecimal totalTaxes;
    private BigDecimal totalBenefits;
    private BigDecimal totalDonations;
    private BigDecimal totalDeductions;
    private BigDecimal totalPayrollCost;
    private BigDecimal totalBonus;
    private BigDecimal totalClaimDeduction;
    private BigDecimal totalClaimReimbursement;

    private Boolean canEditPaydate;
    private Boolean canPostPayrunTransactions;
    private Boolean hasDirectDepositPayments;
    private Boolean hasNonDirectDepositPayments;

    // JSON strings for complex fields (you can model them more strictly if desired)
    private String earningJson;
    private String variablePayEarningsListJson;
    private String deductionsJson;
    private String expenseBatchesDetailsJson;
    
    private BigDecimal totalEpfContribution;
    private BigDecimal totalEsiContribution;
    private BigDecimal totalEdliContribution;
    private BigDecimal totalEpfAdminCharges;

    
    public BigDecimal getTotalEpfContribution() {
        return totalEpfContribution;
    }
    public void setTotalEpfContribution(BigDecimal totalEpfContribution) {
        this.totalEpfContribution = totalEpfContribution;
    }

    public BigDecimal getTotalEsiContribution() {
        return totalEsiContribution;
    }
    public void setTotalEsiContribution(BigDecimal totalEsiContribution) {
        this.totalEsiContribution = totalEsiContribution;
    }

    public BigDecimal getTotalEdliContribution() {
        return totalEdliContribution;
    }
    public void setTotalEdliContribution(BigDecimal totalEdliContribution) {
        this.totalEdliContribution = totalEdliContribution;
    }

    public BigDecimal getTotalEpfAdminCharges() {
        return totalEpfAdminCharges;
    }
    public void setTotalEpfAdminCharges(BigDecimal totalEpfAdminCharges) {
        this.totalEpfAdminCharges = totalEpfAdminCharges;
    }


    private String rejectedReason;

    public String getPayrunId() {
        return payrunId;
    }

    public void setPayrunId(String payrunId) {
        this.payrunId = payrunId;
    }

    public PayRunType getType() {
        return type;
    }

    public void setType(PayRunType type) {
        this.type = type;
    }

    public PayRunStatus getStatus() {
        return status;
    }

    public void setStatus(PayRunStatus status) {
        this.status = status;
    }

    public Boolean getPaymentDue() {
        return isPaymentDue;
    }

    public void setPaymentDue(Boolean paymentDue) {
        isPaymentDue = paymentDue;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
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

    public String getProcessingPeriod() {
        return processingPeriod;
    }

    public void setProcessingPeriod(String processingPeriod) {
        this.processingPeriod = processingPeriod;
    }

    public BigDecimal getPayrollTotal() {
        return payrollTotal;
    }

    public void setPayrollTotal(BigDecimal payrollTotal) {
        this.payrollTotal = payrollTotal;
    }

    public String getStatusInfo() {
        return statusInfo;
    }

    public void setStatusInfo(String statusInfo) {
        this.statusInfo = statusInfo;
    }

    public Integer getNoOfEmployees() {
        return noOfEmployees;
    }

    public void setNoOfEmployees(Integer noOfEmployees) {
        this.noOfEmployees = noOfEmployees;
    }

    public String getApprovalType() {
        return approvalType;
    }

    public void setApprovalType(String approvalType) {
        this.approvalType = approvalType;
    }

    public String getApprovalDetails() {
        return approvalDetails;
    }

    public void setApprovalDetails(String approvalDetails) {
        this.approvalDetails = approvalDetails;
    }

    public String getCompensationName() {
        return compensationName;
    }

    public void setCompensationName(String compensationName) {
        this.compensationName = compensationName;
    }

    public BigDecimal getTotalNetPay() {
        return totalNetPay;
    }

    public void setTotalNetPay(BigDecimal totalNetPay) {
        this.totalNetPay = totalNetPay;
    }

    public BigDecimal getTotalTaxes() {
        return totalTaxes;
    }

    public void setTotalTaxes(BigDecimal totalTaxes) {
        this.totalTaxes = totalTaxes;
    }

    public BigDecimal getTotalBenefits() {
        return totalBenefits;
    }

    public void setTotalBenefits(BigDecimal totalBenefits) {
        this.totalBenefits = totalBenefits;
    }

    public BigDecimal getTotalDonations() {
        return totalDonations;
    }

    public void setTotalDonations(BigDecimal totalDonations) {
        this.totalDonations = totalDonations;
    }

    public BigDecimal getTotalDeductions() {
        return totalDeductions;
    }

    public void setTotalDeductions(BigDecimal totalDeductions) {
        this.totalDeductions = totalDeductions;
    }

    public BigDecimal getTotalPayrollCost() {
        return totalPayrollCost;
    }

    public void setTotalPayrollCost(BigDecimal totalPayrollCost) {
        this.totalPayrollCost = totalPayrollCost;
    }

    public Boolean getCanEditPaydate() {
        return canEditPaydate;
    }

    public void setCanEditPaydate(Boolean canEditPaydate) {
        this.canEditPaydate = canEditPaydate;
    }

    public Boolean getCanPostPayrunTransactions() {
        return canPostPayrunTransactions;
    }

    public void setCanPostPayrunTransactions(Boolean canPostPayrunTransactions) {
        this.canPostPayrunTransactions = canPostPayrunTransactions;
    }

    public Boolean getHasDirectDepositPayments() {
        return hasDirectDepositPayments;
    }

    public void setHasDirectDepositPayments(Boolean hasDirectDepositPayments) {
        this.hasDirectDepositPayments = hasDirectDepositPayments;
    }

    public Boolean getHasNonDirectDepositPayments() {
        return hasNonDirectDepositPayments;
    }

    public void setHasNonDirectDepositPayments(Boolean hasNonDirectDepositPayments) {
        this.hasNonDirectDepositPayments = hasNonDirectDepositPayments;
    }

    public String getEarningJson() {
        return earningJson;
    }

    public void setEarningJson(String earningJson) {
        this.earningJson = earningJson;
    }

    public String getVariablePayEarningsListJson() {
        return variablePayEarningsListJson;
    }

    public void setVariablePayEarningsListJson(String variablePayEarningsListJson) {
        this.variablePayEarningsListJson = variablePayEarningsListJson;
    }

    public String getDeductionsJson() {
        return deductionsJson;
    }

    public void setDeductionsJson(String deductionsJson) {
        this.deductionsJson = deductionsJson;
    }

    public String getExpenseBatchesDetailsJson() {
        return expenseBatchesDetailsJson;
    }

    public void setExpenseBatchesDetailsJson(String expenseBatchesDetailsJson) {
        this.expenseBatchesDetailsJson = expenseBatchesDetailsJson;
    }

    public String getRejectedReason() {
        return rejectedReason;
    }

    public void setRejectedReason(String rejectedReason) {
        this.rejectedReason = rejectedReason;
    }

    public LocalDate getApprovedDate() {
        return approvedDate;
    }

    public void setApprovedDate(LocalDate approvedDate) {
        this.approvedDate = approvedDate;
    }

    public BigDecimal getTotalBonus() {
        return totalBonus;
    }

    public void setTotalBonus(BigDecimal totalBonus) {
        this.totalBonus = totalBonus;
    }

    public BigDecimal getTotalClaimDeduction() {
        return totalClaimDeduction;
    }

    public void setTotalClaimDeduction(BigDecimal totalClaimDeduction) {
        this.totalClaimDeduction = totalClaimDeduction;
    }

    public BigDecimal getTotalClaimReimbursement() {
        return totalClaimReimbursement;
    }

    public void setTotalClaimReimbursement(BigDecimal totalClaimReimbursement) {
        this.totalClaimReimbursement = totalClaimReimbursement;
    }
}