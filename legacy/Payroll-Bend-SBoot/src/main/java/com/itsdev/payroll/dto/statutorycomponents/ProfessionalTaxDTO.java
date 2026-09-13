package com.itsdev.payroll.dto.statutorycomponents;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ProfessionalTaxDTO {
    private String taxId;
    private String state;
    private String stateCode;
    private String locationName;
    private String registrationNumber;
    private String registrationDate;

    private String taxConfigurationFrequency;
    private String grossSalaryConfigurationFrequency;
    private String deductionFrequency;

    private boolean isProfessionalTaxSupported;
    private boolean isReadOnly;

    private String effectiveFrom;

    private List<SlabDetailDTO> slabDetails;
    private List<SlabRateConfigurationDTO> slabRateConfigurations;

    // Getters & Setters


    public String getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(String effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public String getTaxId() {
        return taxId;
    }

    public void setTaxId(String taxId) {
        this.taxId = taxId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getStateCode() {
        return stateCode;
    }

    public void setStateCode(String stateCode) {
        this.stateCode = stateCode;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(String registrationDate) {
        this.registrationDate = registrationDate;
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

    public boolean isProfessionalTaxSupported() {
        return isProfessionalTaxSupported;
    }

    public void setProfessionalTaxSupported(boolean professionalTaxSupported) {
        isProfessionalTaxSupported = professionalTaxSupported;
    }

    public boolean isReadOnly() {
        return isReadOnly;
    }

    public void setReadOnly(boolean readOnly) {
        isReadOnly = readOnly;
    }

    public List<SlabDetailDTO> getSlabDetails() {
        return slabDetails;
    }

    public void setSlabDetails(List<SlabDetailDTO> slabDetails) {
        this.slabDetails = slabDetails;
    }

    public List<SlabRateConfigurationDTO> getSlabRateConfigurations() {
        return slabRateConfigurations;
    }

    public void setSlabRateConfigurations(List<SlabRateConfigurationDTO> slabRateConfigurations) {
        this.slabRateConfigurations = slabRateConfigurations;
    }
}
