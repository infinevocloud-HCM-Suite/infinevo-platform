package com.itsdev.payroll.dto.payRun.oneTimePayout;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class OneTimePayoutRequestDTO {

    private String earningId;   // from Earning table
    private LocalDate payDate;

    private List<EmployeePayoutDTO> employees; // employee + amount list

    public String getEarningId() {
        return earningId;
    }

    public void setEarningId(String earningId) {
        this.earningId = earningId;
    }

    public LocalDate getPayDate() {
        return payDate;
    }

    public void setPayDate(LocalDate payDate) {
        this.payDate = payDate;
    }

    public List<EmployeePayoutDTO> getEmployees() {
        return employees;
    }

    public void setEmployees(List<EmployeePayoutDTO> employees) {
        this.employees = employees;
    }

    public static class EmployeePayoutDTO {
        private Long employeeId;
        private BigDecimal earningAmount;
        private List<String> taxes; // currently empty, but extensible

        private Integer days;  // For Leave Encashment


        public Integer getDays() {
            return days;
        }

        public void setDays(Integer days) {
            this.days = days;
        }

        public Long getEmployeeId() {
            return employeeId;
        }

        public void setEmployeeId(Long employeeId) {
            this.employeeId = employeeId;
        }

        public BigDecimal getEarningAmount() {
            return earningAmount;
        }

        public void setEarningAmount(BigDecimal earningAmount) {
            this.earningAmount = earningAmount;
        }

        public List<String> getTaxes() {
            return taxes;
        }

        public void setTaxes(List<String> taxes) {
            this.taxes = taxes;
        }
    }
}

