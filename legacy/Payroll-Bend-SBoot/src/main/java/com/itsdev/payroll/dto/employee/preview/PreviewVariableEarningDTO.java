package com.itsdev.payroll.dto.employee.preview;

public class PreviewVariableEarningDTO {
    private String id;
    private String variableCode; // e.g., BONUS_Q1
    private Boolean enabled;
    private Double amount;
    private Double amountInPercentage;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVariableCode() {
        return variableCode;
    }

    public void setVariableCode(String variableCode) {
        this.variableCode = variableCode;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public Double getAmountInPercentage() {
        return amountInPercentage;
    }

    public void setAmountInPercentage(Double amountInPercentage) {
        this.amountInPercentage = amountInPercentage;
    }
}

