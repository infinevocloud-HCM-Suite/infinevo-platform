package com.itsdev.payroll.dto.payruns;

import com.itsdev.payroll.enumeration.payruns.PayRunStatus;
import com.itsdev.payroll.enumeration.payruns.PayRunType;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PersistedPayRunDTO {

    private String payrunId;
    private Boolean isPaymentDue;
    private PayRunStatus status;
    private String paymentStatus;
    private LocalDate payPeriodStartDate;
    private LocalDate payPeriodEndDate;
    private LocalDate payDate;
    private PayRunType type;
    private String processingPeriod;
    private BigDecimal payrollTotal;
    private String statusInfo;
    private Integer noOfEmployees;
    private String approvalType;
    private String approvalDetails;
    private String compensationName;

    public String getPayrunId() {
        return payrunId;
    }

    public void setPayrunId(String payrunId) {
        this.payrunId = payrunId;
    }

    public Boolean getPaymentDue() {
        return isPaymentDue;
    }

    public void setPaymentDue(Boolean paymentDue) {
        isPaymentDue = paymentDue;
    }

    public PayRunStatus getStatus() {
        return status;
    }

    public void setStatus(PayRunStatus status) {
        this.status = status;
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

    public PayRunType getType() {
        return type;
    }

    public void setType(PayRunType type) {
        this.type = type;
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
}
