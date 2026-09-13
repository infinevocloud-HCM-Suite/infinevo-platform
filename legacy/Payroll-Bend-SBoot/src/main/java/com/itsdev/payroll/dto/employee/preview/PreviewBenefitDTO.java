package com.itsdev.payroll.dto.employee.preview;


public class PreviewBenefitDTO {
    private String id;            // benefitId from master
    private String name;          // benefitName from master
    private Boolean enabled;
    private Double amount;
    private Double amountInPercentage;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

