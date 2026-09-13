package com.itsdev.payroll.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

public class DashboardResponse {

    // --- Top section ---
    private EmployeeSummary employeeSummary;
    private boolean firstPayrollCompleted;
    private boolean bankAccountsMismatchExist;
    private boolean hasDirectDepositPayments;

    // --- Payrun section ---
    private List<PayRunSummary> payrollRuns;

    // --- Monthly summary (chart data) ---
    private PayrollSummary payrollSummary;


    // ================== INNER DTO CLASSES ==================

    // 👤 Employee summary (active + unfinished employees)
    public static class EmployeeSummary {
        private int activeEmployees;
        private int unfinishedEmployees;

        public int getActiveEmployees() {
            return activeEmployees;
        }
        public void setActiveEmployees(int activeEmployees) {
            this.activeEmployees = activeEmployees;
        }
        public int getUnfinishedEmployees() {
            return unfinishedEmployees;
        }
        public void setUnfinishedEmployees(int unfinishedEmployees) {
            this.unfinishedEmployees = unfinishedEmployees;
        }
    }

    // 💰 Payrun details (cards displayed on dashboard)
    public static class PayRunSummary {
        private String payrunId;
        private String processingPeriod;
        private String payDate;
        private String status;
        private boolean paymentDue;
        private BigDecimal payrollTotal;
        private int noOfEmployees;

        public String getPayrunId() {
            return payrunId;
        }
        public void setPayrunId(String payrunId) {
            this.payrunId = payrunId;
        }
        public String getProcessingPeriod() {
            return processingPeriod;
        }
        public void setProcessingPeriod(String processingPeriod) {
            this.processingPeriod = processingPeriod;
        }
        public String getPayDate() {
            return payDate;
        }
        public void setPayDate(String payDate) {
            this.payDate = payDate;
        }
        public String getStatus() {
            return status;
        }
        public void setStatus(String status) {
            this.status = status;
        }
        public boolean isPaymentDue() {
            return paymentDue;
        }
        public void setPaymentDue(boolean paymentDue) {
            this.paymentDue = paymentDue;
        }
        public BigDecimal getPayrollTotal() {
            return payrollTotal;
        }
        public void setPayrollTotal(BigDecimal payrollTotal) {
            this.payrollTotal = payrollTotal;
        }
        public int getNoOfEmployees() {
            return noOfEmployees;
        }
        public void setNoOfEmployees(int noOfEmployees) {
            this.noOfEmployees = noOfEmployees;
        }
    }

    // 📊 Monthly payroll summary
    public static class PayrollSummary {
        private List<MonthDetail> monthDetails;

        public List<MonthDetail> getMonthDetails() {
            return monthDetails;
        }
        public void setMonthDetails(List<MonthDetail> monthDetails) {
            this.monthDetails = monthDetails;
        }
    }

    // 🗓️ Each month’s details for the payroll chart
    public static class MonthDetail {
        private String monthName;
        private BigDecimal totalEarnings;
        private BigDecimal totalNetPay;
        private BigDecimal totalTaxes;
        private BigDecimal totalDeductions;

        public String getMonthName() {
            return monthName;
        }
        public void setMonthName(String monthName) {
            this.monthName = monthName;
        }
        public BigDecimal getTotalEarnings() {
            return totalEarnings;
        }
        public void setTotalEarnings(BigDecimal totalEarnings) {
            this.totalEarnings = totalEarnings;
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
        public BigDecimal getTotalDeductions() {
            return totalDeductions;
        }
        public void setTotalDeductions(BigDecimal totalDeductions) {
            this.totalDeductions = totalDeductions;
        }
    }

    public EmployeeSummary getEmployeeSummary() {
        return employeeSummary;
    }
    public void setEmployeeSummary(EmployeeSummary employeeSummary) {
        this.employeeSummary = employeeSummary;
    }

    public boolean isFirstPayrollCompleted() {
        return firstPayrollCompleted;
    }
    public void setFirstPayrollCompleted(boolean firstPayrollCompleted) {
        this.firstPayrollCompleted = firstPayrollCompleted;
    }

    public boolean isBankAccountsMismatchExist() {
        return bankAccountsMismatchExist;
    }
    public void setBankAccountsMismatchExist(boolean bankAccountsMismatchExist) {
        this.bankAccountsMismatchExist = bankAccountsMismatchExist;
    }

    public boolean isHasDirectDepositPayments() {
        return hasDirectDepositPayments;
    }
    public void setHasDirectDepositPayments(boolean hasDirectDepositPayments) {
        this.hasDirectDepositPayments = hasDirectDepositPayments;
    }

    public List<PayRunSummary> getPayrollRuns() {
        return payrollRuns;
    }
    public void setPayrollRuns(List<PayRunSummary> payrollRuns) {
        this.payrollRuns = payrollRuns;
    }

    public PayrollSummary getPayrollSummary() {
        return payrollSummary;
    }
    public void setPayrollSummary(PayrollSummary payrollSummary) {
        this.payrollSummary = payrollSummary;
    }
}
