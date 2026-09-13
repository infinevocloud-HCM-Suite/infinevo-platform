package com.itsdev.payroll.dto.salarycomponents;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;

public class EarningDTO {
    private String earningId;
    
    @NotBlank(message = "Earning name is required")
    private String earningName;
    
    private String earningType;
    private String earningTypeFormatted;
    private String displayName;

    private BigDecimal amount;
    private String amountFormatted;

    private BigDecimal value;
    private String valueFormatted;
    private String valueType;
    private String maxLimit;

    private Boolean isAmountInPercentage;
    private Boolean isProRata;
    private Boolean isIncludedInCtc;
    private Boolean isIncludedInSalaryStructure;

    private String status = "active";
    private String statusFormatted;
    private Boolean isFbpComponent;
    private Boolean isVariable;
    private Boolean canIncludeAsFbp;
    private String componentType;
    private Boolean isOneTimeComponent;
    private Boolean isUserConfigurable;
    private Boolean isAssociatedWithEmployee;
    private Boolean canEditProrataConfiguration;
    private Boolean canChangeScheduleEarningConfiguration;
    private Boolean canChangePayType;

    private String gratuityExemptionRuleDetails;

    private Boolean isIncludedInEpf;
    private String epfInclusionType;
    private String epfInclusionTypeFormatted;
    private Boolean canChangeEpfConfiguration;
    private Boolean canChangeEsiConfiguration;
    private Boolean isIncludedInEsi;
    private Boolean isTaxable;
    private Boolean showInPayslip;
    private Boolean canCalculateTaxWithoutProjection;
    private Boolean isOptIn;

    private String parentEarningId;
    private String parentEarningName;

    private Boolean isFormulaBasedCalculationSupported;
    private String formulaBasedOn;

    private Boolean isScheduledEarning;
    private Boolean canChangeDefaultPercentageVariables;

    private String earningFrequency;

    private LocalDateTime createdTime;

    private Boolean isDeleted = false;


    public String getEarningId() {
        return earningId;
    }

    public void setEarningId(String earningId) {
        this.earningId = earningId;
    }

    public String getEarningName() {
        return earningName;
    }

    public void setEarningName(String earningName) {
        this.earningName = earningName;
    }

    public String getEarningType() {
        return earningType;
    }

    public void setEarningType(String earningType) {
        this.earningType = earningType;
    }

    public String getEarningTypeFormatted() {
        return earningTypeFormatted;
    }

    public void setEarningTypeFormatted(String earningTypeFormatted) {
        this.earningTypeFormatted = earningTypeFormatted;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getAmountFormatted() {
        return amountFormatted;
    }

    public void setAmountFormatted(String amountFormatted) {
        this.amountFormatted = amountFormatted;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public String getValueFormatted() {
        return valueFormatted;
    }

    public void setValueFormatted(String valueFormatted) {
        this.valueFormatted = valueFormatted;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getMaxLimit() {
        return maxLimit;
    }

    public void setMaxLimit(String maxLimit) {
        this.maxLimit = maxLimit;
    }

    public Boolean getIsAmountInPercentage() {
        return isAmountInPercentage;
    }

    public void setIsAmountInPercentage(Boolean isAmountInPercentage) {
        this.isAmountInPercentage = isAmountInPercentage;
    }

    public Boolean getIsProRata() {
        return isProRata;
    }

    public void setIsProRata(Boolean isProRata) {
        this.isProRata = isProRata;
    }

    public Boolean getIsIncludedInCtc() {
        return isIncludedInCtc;
    }

    public void setIsIncludedInCtc(Boolean isIncludedInCtc) {
        this.isIncludedInCtc = isIncludedInCtc;
    }

    public Boolean getIsIncludedInSalaryStructure() {
        return isIncludedInSalaryStructure;
    }

    public void setIsIncludedInSalaryStructure(Boolean isIncludedInSalaryStructure) {
        this.isIncludedInSalaryStructure = isIncludedInSalaryStructure;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusFormatted() {
        return statusFormatted;
    }

    public void setStatusFormatted(String statusFormatted) {
        this.statusFormatted = statusFormatted;
    }

    public Boolean getIsFbpComponent() {
        return isFbpComponent;
    }

    public void setIsFbpComponent(Boolean isFbpComponent) {
        this.isFbpComponent = isFbpComponent;
    }

    public Boolean getIsVariable() {
        return isVariable;
    }

    public void setIsVariable(Boolean isVariable) {
        this.isVariable = isVariable;
    }

    public Boolean getCanIncludeAsFbp() {
        return canIncludeAsFbp;
    }

    public void setCanIncludeAsFbp(Boolean canIncludeAsFbp) {
        this.canIncludeAsFbp = canIncludeAsFbp;
    }

    public String getComponentType() {
        return componentType;
    }

    public void setComponentType(String componentType) {
        this.componentType = componentType;
    }

    public Boolean getIsOneTimeComponent() {
        return isOneTimeComponent;
    }

    public void setIsOneTimeComponent(Boolean isOneTimeComponent) {
        this.isOneTimeComponent = isOneTimeComponent;
    }

    public Boolean getIsUserConfigurable() {
        return isUserConfigurable;
    }

    public void setIsUserConfigurable(Boolean isUserConfigurable) {
        this.isUserConfigurable = isUserConfigurable;
    }

    public Boolean getIsAssociatedWithEmployee() {
        return isAssociatedWithEmployee;
    }

    public void setIsAssociatedWithEmployee(Boolean isAssociatedWithEmployee) {
        this.isAssociatedWithEmployee = isAssociatedWithEmployee;
    }

    public Boolean getCanEditProrataConfiguration() {
        return canEditProrataConfiguration;
    }

    public void setCanEditProrataConfiguration(Boolean canEditProrataConfiguration) {
        this.canEditProrataConfiguration = canEditProrataConfiguration;
    }

    public Boolean getCanChangeScheduleEarningConfiguration() {
        return canChangeScheduleEarningConfiguration;
    }

    public void setCanChangeScheduleEarningConfiguration(Boolean canChangeScheduleEarningConfiguration) {
        this.canChangeScheduleEarningConfiguration = canChangeScheduleEarningConfiguration;
    }

    public Boolean getCanChangePayType() {
        return canChangePayType;
    }

    public void setCanChangePayType(Boolean canChangePayType) {
        this.canChangePayType = canChangePayType;
    }

    public String getGratuityExemptionRuleDetails() {
        return gratuityExemptionRuleDetails;
    }

    public void setGratuityExemptionRuleDetails(String gratuityExemptionRuleDetails) {
        this.gratuityExemptionRuleDetails = gratuityExemptionRuleDetails;
    }

    public Boolean getIsIncludedInEpf() {
        return isIncludedInEpf;
    }

    public void setIsIncludedInEpf(Boolean isIncludedInEpf) {
        this.isIncludedInEpf = isIncludedInEpf;
    }

    public String getEpfInclusionType() {
        return epfInclusionType;
    }

    public void setEpfInclusionType(String epfInclusionType) {
        this.epfInclusionType = epfInclusionType;
    }

    public String getEpfInclusionTypeFormatted() {
        return epfInclusionTypeFormatted;
    }

    public void setEpfInclusionTypeFormatted(String epfInclusionTypeFormatted) {
        this.epfInclusionTypeFormatted = epfInclusionTypeFormatted;
    }

    public Boolean getCanChangeEpfConfiguration() {
        return canChangeEpfConfiguration;
    }

    public void setCanChangeEpfConfiguration(Boolean canChangeEpfConfiguration) {
        this.canChangeEpfConfiguration = canChangeEpfConfiguration;
    }

    public Boolean getCanChangeEsiConfiguration() {
        return canChangeEsiConfiguration;
    }

    public void setCanChangeEsiConfiguration(Boolean canChangeEsiConfiguration) {
        this.canChangeEsiConfiguration = canChangeEsiConfiguration;
    }

    public Boolean getIsIncludedInEsi() {
        return isIncludedInEsi;
    }

    public void setIsIncludedInEsi(Boolean isIncludedInEsi) {
        this.isIncludedInEsi = isIncludedInEsi;
    }

    public Boolean getIsTaxable() {
        return isTaxable;
    }

    public void setIsTaxable(Boolean isTaxable) {
        this.isTaxable = isTaxable;
    }

    public Boolean getShowInPayslip() {
        return showInPayslip;
    }

    public void setShowInPayslip(Boolean showInPayslip) {
        this.showInPayslip = showInPayslip;
    }

    public Boolean getCanCalculateTaxWithoutProjection() {
        return canCalculateTaxWithoutProjection;
    }

    public void setCanCalculateTaxWithoutProjection(Boolean canCalculateTaxWithoutProjection) {
        this.canCalculateTaxWithoutProjection = canCalculateTaxWithoutProjection;
    }

    public Boolean getIsOptIn() {
        return isOptIn;
    }

    public void setIsOptIn(Boolean isOptIn) {
        this.isOptIn = isOptIn;
    }

    public String getParentEarningId() {
        return parentEarningId;
    }

    public void setParentEarningId(String parentEarningId) {
        this.parentEarningId = parentEarningId;
    }

    public String getParentEarningName() {
        return parentEarningName;
    }

    public void setParentEarningName(String parentEarningName) {
        this.parentEarningName = parentEarningName;
    }

    public Boolean getIsFormulaBasedCalculationSupported() {
        return isFormulaBasedCalculationSupported;
    }

    public void setIsFormulaBasedCalculationSupported(Boolean isFormulaBasedCalculationSupported) {
        this.isFormulaBasedCalculationSupported = isFormulaBasedCalculationSupported;
    }

    public String getFormulaBasedOn() {
        return formulaBasedOn;
    }

    public void setFormulaBasedOn(String formulaBasedOn) {
        this.formulaBasedOn = formulaBasedOn;
    }

    public Boolean getIsScheduledEarning() {
        return isScheduledEarning;
    }

    public void setIsScheduledEarning(Boolean isScheduledEarning) {
        this.isScheduledEarning = isScheduledEarning;
    }

    public Boolean getCanChangeDefaultPercentageVariables() {
        return canChangeDefaultPercentageVariables;
    }

    public void setCanChangeDefaultPercentageVariables(Boolean canChangeDefaultPercentageVariables) {
        this.canChangeDefaultPercentageVariables = canChangeDefaultPercentageVariables;
    }

    public String getEarningFrequency() {
        return earningFrequency;
    }

    public void setEarningFrequency(String earningFrequency) {
        this.earningFrequency = earningFrequency;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

}
