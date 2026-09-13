package com.itsdev.payroll.dto.payRun.oneTimePayout;


import java.time.LocalDate;
import java.util.List;

public class OneTimePayoutImportRequestDTO {
    private String earningId;
    private LocalDate payDate;
    private List<OneTimePayoutImportDTO> employees;

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

    public List<OneTimePayoutImportDTO> getEmployees() {
        return employees;
    }

    public void setEmployees(List<OneTimePayoutImportDTO> employees) {
        this.employees = employees;
    }
}

