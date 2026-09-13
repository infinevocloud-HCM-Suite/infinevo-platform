package com.itsdev.payroll.dto.payruns;

import com.itsdev.payroll.enumeration.payruns.PayRunStatus;
import com.itsdev.payroll.enumeration.payruns.PayRunType;

import java.time.LocalDate;

public class ReadyPayRunDTO {
    private PayRunType type;
    private PayRunStatus status;
    private LocalDate payPeriodStartDate;
    private LocalDate payPeriodEndDate;
    private LocalDate payDate;
    private String processingPeriod;
    private Integer noOfEmployees;
    private Boolean paymentDue;
    private String statusInfo;

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

    public Integer getNoOfEmployees() {
        return noOfEmployees;
    }

    public void setNoOfEmployees(Integer noOfEmployees) {
        this.noOfEmployees = noOfEmployees;
    }

    public Boolean getPaymentDue() {
        return paymentDue;
    }

    public void setPaymentDue(Boolean paymentDue) {
        this.paymentDue = paymentDue;
    }

    public String getStatusInfo() {
        return statusInfo;
    }

    public void setStatusInfo(String statusInfo) {
        this.statusInfo = statusInfo;
    }
}
