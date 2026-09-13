package com.itsdev.payroll.dto.payRun.offcyclepayrun;


import java.math.BigDecimal;

public class OffCyclePayrunEmployeeEarningDTO {

    private String earningId;
    private BigDecimal amount;
    private String days;

    // Getters & Setters
    public String getEarningId() {
        return earningId;
    }

    public void setEarningId(String earningId) {
        this.earningId = earningId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDays() {
        return days;
    }

    public void setDays(String days) {
        this.days = days;
    }
}

