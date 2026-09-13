package com.itsdev.payroll.dto.employee.preview;

public class PreviewEarningDTO {
    private String id;                // earningId from master
    private String name;              // displayName or earningName from master
    private Boolean enabled;
    private Double amount;
    private Double amountInPercentage;
    private Boolean isAmountInPercentage;
    private Boolean isIncludedInPf;
    private Boolean isIncludedInEsi;
    private Boolean isAssociatedWithEmployee;
    private Boolean isScheduledEarning;
    private Boolean isVariable;
    private String earningFrequency;

    private Double overrideAmount;
    private String calculationBasis;


    // --- getters / setters ---


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

    public Boolean getIsAmountInPercentage() {
        return isAmountInPercentage;
    }
    public void setIsAmountInPercentage(Boolean isAmountInPercentage) {
        this.isAmountInPercentage = isAmountInPercentage;
    }

    public Boolean getIsIncludedInPf() {
        return isIncludedInPf;
    }
    public void setIsIncludedInPf(Boolean isIncludedInPf) {
        this.isIncludedInPf = isIncludedInPf;
    }

    public Boolean getIsIncludedInEsi() {
        return isIncludedInEsi;
    }
    public void setIsIncludedInEsi(Boolean isIncludedInEsi) {
        this.isIncludedInEsi = isIncludedInEsi;
    }

    public Boolean getIsAssociatedWithEmployee() {
        return isAssociatedWithEmployee;
    }
    public void setIsAssociatedWithEmployee(Boolean isAssociatedWithEmployee) {
        this.isAssociatedWithEmployee = isAssociatedWithEmployee;
    }

    public Boolean getIsScheduledEarning() {
        return isScheduledEarning;
    }
    public void setIsScheduledEarning(Boolean isScheduledEarning) {
        this.isScheduledEarning = isScheduledEarning;
    }

    public Boolean getIsVariable() {
        return isVariable;
    }
    public void setIsVariable(Boolean isVariable) {
        this.isVariable = isVariable;
    }

    public String getEarningFrequency() {
        return earningFrequency;
    }
    public void setEarningFrequency(String earningFrequency) {
        this.earningFrequency = earningFrequency;
    }

    public Double getOverrideAmount() {
        return overrideAmount;
    }

    public void setOverrideAmount(Double overrideAmount) {
        this.overrideAmount = overrideAmount;
    }
}
