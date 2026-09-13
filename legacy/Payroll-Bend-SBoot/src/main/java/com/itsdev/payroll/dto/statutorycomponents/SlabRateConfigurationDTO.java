package com.itsdev.payroll.dto.statutorycomponents;

import java.util.List;

public class SlabRateConfigurationDTO {
    private String taxRateSettingsId;
    private String taxConfigurationFrequency;
    private String grossSalaryConfigurationFrequency;
    private String deductionFrequency;

    private String effectiveFrom;
    private String effectiveTo;

    private boolean isActive;
    private boolean isEditable;

    private List<SlabDetailDTO> slabDetails;

    // Getters & Setters

    public String getTaxRateSettingsId() {
        return taxRateSettingsId;
    }

    public void setTaxRateSettingsId(String taxRateSettingsId) {
        this.taxRateSettingsId = taxRateSettingsId;
    }

    public String getTaxConfigurationFrequency() {
        return taxConfigurationFrequency;
    }

    public void setTaxConfigurationFrequency(String taxConfigurationFrequency) {
        this.taxConfigurationFrequency = taxConfigurationFrequency;
    }

    public String getGrossSalaryConfigurationFrequency() {
        return grossSalaryConfigurationFrequency;
    }

    public void setGrossSalaryConfigurationFrequency(String grossSalaryConfigurationFrequency) {
        this.grossSalaryConfigurationFrequency = grossSalaryConfigurationFrequency;
    }

    public String getDeductionFrequency() {
        return deductionFrequency;
    }

    public void setDeductionFrequency(String deductionFrequency) {
        this.deductionFrequency = deductionFrequency;
    }

    public String getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(String effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public String getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(String effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public boolean isEditable() {
        return isEditable;
    }

    public void setEditable(boolean editable) {
        isEditable = editable;
    }

    public List<SlabDetailDTO> getSlabDetails() {
        return slabDetails;
    }

    public void setSlabDetails(List<SlabDetailDTO> slabDetails) {
        this.slabDetails = slabDetails;
    }
}
