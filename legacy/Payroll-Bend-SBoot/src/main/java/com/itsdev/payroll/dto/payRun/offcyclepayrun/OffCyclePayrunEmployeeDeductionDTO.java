package com.itsdev.payroll.dto.payRun.offcyclepayrun;

import java.math.BigDecimal;

public class OffCyclePayrunEmployeeDeductionDTO {

    private String deductionId;
    private BigDecimal amount;

    // Getters & Setters
    public String getDeductionId() {
        return deductionId;
    }

    public void setDeductionId(String deductionId) {
        this.deductionId = deductionId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
