package com.itsdev.payroll.dto.employee;

public class EmployeeEarningDTO {

    private String id; // maps to earningId from master
    private String earningCode; // maps to employee_earnings.earningCode
    private String name; // maps from earning.earningName
    private Boolean enabled;
    private Double amount;
    private Double amountInPercentage;
    private Boolean editable;
    private Boolean isVariable;

    private Double overrideAmount;

    private String calculationBasis;

    private String earningFrequency;

    public String getCalculationBasis() {
        return calculationBasis;
    }

    public void setCalculationBasis(String calculationBasis) {
        this.calculationBasis = calculationBasis;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEarningCode() {
        return earningCode;
    }

    public void setEarningCode(String earningCode) {
        this.earningCode = earningCode;
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

    public Boolean getEditable() {
        return editable;
    }

    public void setEditable(Boolean editable) {
        this.editable = editable;
    }

    public Boolean getIsVariable() {
        return isVariable;
    }

    public void setIsVariable(Boolean isVariable) {
        this.isVariable = isVariable;
    }

    public Double getOverrideAmount() {
        return overrideAmount;
    }

    public void setOverrideAmount(Double overrideAmount) {
        this.overrideAmount = overrideAmount;
    }

    public String getEarningFrequency() {
        return earningFrequency;
    }

    public void setEarningFrequency(String earningFrequency) {
        this.earningFrequency = earningFrequency;
    }
}
