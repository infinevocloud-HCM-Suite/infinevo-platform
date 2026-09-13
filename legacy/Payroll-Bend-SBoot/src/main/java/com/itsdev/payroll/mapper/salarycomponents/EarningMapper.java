package com.itsdev.payroll.mapper.salarycomponents;

import com.itsdev.payroll.dto.salarycomponents.EarningDTO;
import com.itsdev.payroll.entity.salarycomponents.Earning;

public class EarningMapper {

    public static EarningDTO toDTO(Earning entity) {
        EarningDTO dto = new EarningDTO();
        dto.setEarningId(entity.getEarningId());
        dto.setEarningName(entity.getEarningName());
        dto.setEarningType(entity.getEarningType());
        dto.setEarningTypeFormatted(entity.getEarningTypeFormatted());
        dto.setDisplayName(entity.getDisplayName());
        dto.setAmount(entity.getAmount());
        dto.setAmountFormatted(entity.getAmountFormatted());
        dto.setValue(entity.getValue());
        dto.setValueFormatted(entity.getValueFormatted());
        dto.setValueType(entity.getValueType());
        dto.setMaxLimit(entity.getMaxLimit());
        dto.setIsAmountInPercentage(entity.getIsAmountInPercentage());
        dto.setIsProRata(entity.getIsProRata());
        dto.setIsIncludedInCtc(entity.getIsIncludedInCtc());
        dto.setIsIncludedInSalaryStructure(entity.getIsIncludedInSalaryStructure());
        dto.setStatus(entity.getStatus());
        dto.setStatusFormatted(entity.getStatusFormatted());
        dto.setIsFbpComponent(entity.getIsFbpComponent());
        dto.setIsVariable(entity.getIsVariable());
        dto.setCanIncludeAsFbp(entity.getCanIncludeAsFbp());
        dto.setComponentType(entity.getComponentType());
        dto.setIsOneTimeComponent(entity.getIsOneTimeComponent());
        dto.setIsUserConfigurable(entity.getIsUserConfigurable());
        dto.setIsAssociatedWithEmployee(entity.getIsAssociatedWithEmployee());
        dto.setCanEditProrataConfiguration(entity.getCanEditProrataConfiguration());
        dto.setCanChangeScheduleEarningConfiguration(entity.getCanChangeScheduleEarningConfiguration());
        dto.setCanChangePayType(entity.getCanChangePayType());
        dto.setGratuityExemptionRuleDetails(entity.getGratuityExemptionRuleDetails());
        dto.setIsIncludedInEpf(entity.getIsIncludedInEpf());
        dto.setEpfInclusionType(entity.getEpfInclusionType());
        dto.setEpfInclusionTypeFormatted(entity.getEpfInclusionTypeFormatted());
        dto.setCanChangeEpfConfiguration(entity.getCanChangeEpfConfiguration());
        dto.setCanChangeEsiConfiguration(entity.getCanChangeEsiConfiguration());
        dto.setIsIncludedInEsi(entity.getIsIncludedInEsi());
        dto.setIsTaxable(entity.getIsTaxable());
        dto.setShowInPayslip(entity.getShowInPayslip());
        dto.setCanCalculateTaxWithoutProjection(entity.getCanCalculateTaxWithoutProjection());
        dto.setIsOptIn(entity.getIsOptIn());
        dto.setParentEarningId(entity.getParentEarningId());
        dto.setParentEarningName(entity.getParentEarningName());
        dto.setIsFormulaBasedCalculationSupported(entity.getIsFormulaBasedCalculationSupported());
        dto.setFormulaBasedOn(entity.getFormulaBasedOn());
        dto.setIsScheduledEarning(entity.getIsScheduledEarning());
        dto.setCanChangeDefaultPercentageVariables(entity.getCanChangeDefaultPercentageVariables());
        dto.setEarningFrequency(entity.getEarningFrequency());
        dto.setCreatedTime(entity.getCreatedTime());
        return dto;
    }

    public static Earning toEntity(EarningDTO dto) {
        Earning entity = new Earning();
        entity.setEarningName(dto.getEarningName());
        entity.setEarningType(dto.getEarningType());
        entity.setEarningTypeFormatted(dto.getEarningTypeFormatted());
        entity.setDisplayName(dto.getDisplayName());
        entity.setAmount(dto.getAmount());
        entity.setAmountFormatted(dto.getAmountFormatted());
        entity.setValue(dto.getValue());
        entity.setValueFormatted(dto.getValueFormatted());
        entity.setValueType(dto.getValueType());
        entity.setMaxLimit(dto.getMaxLimit());
        entity.setIsAmountInPercentage(dto.getIsAmountInPercentage());
        entity.setIsProRata(dto.getIsProRata());
        entity.setIsIncludedInCtc(dto.getIsIncludedInCtc());
        entity.setIsIncludedInSalaryStructure(dto.getIsIncludedInSalaryStructure());
        entity.setStatus(dto.getStatus());
        entity.setStatusFormatted(dto.getStatusFormatted());
        entity.setIsFbpComponent(dto.getIsFbpComponent());
        entity.setIsVariable(dto.getIsVariable());
        entity.setCanIncludeAsFbp(dto.getCanIncludeAsFbp());
        entity.setComponentType(dto.getComponentType());
        entity.setIsOneTimeComponent(dto.getIsOneTimeComponent());
        entity.setIsUserConfigurable(dto.getIsUserConfigurable());
        entity.setIsAssociatedWithEmployee(dto.getIsAssociatedWithEmployee());
        entity.setCanEditProrataConfiguration(dto.getCanEditProrataConfiguration());
        entity.setCanChangeScheduleEarningConfiguration(dto.getCanChangeScheduleEarningConfiguration());
        entity.setCanChangePayType(dto.getCanChangePayType());
        entity.setGratuityExemptionRuleDetails(dto.getGratuityExemptionRuleDetails());
        entity.setIsIncludedInEpf(dto.getIsIncludedInEpf());
        entity.setEpfInclusionType(dto.getEpfInclusionType());
        entity.setEpfInclusionTypeFormatted(dto.getEpfInclusionTypeFormatted());
        entity.setCanChangeEpfConfiguration(dto.getCanChangeEpfConfiguration());
        entity.setCanChangeEsiConfiguration(dto.getCanChangeEsiConfiguration());
        entity.setIsIncludedInEsi(dto.getIsIncludedInEsi());
        entity.setIsTaxable(dto.getIsTaxable());
        entity.setShowInPayslip(dto.getShowInPayslip());
        entity.setCanCalculateTaxWithoutProjection(dto.getCanCalculateTaxWithoutProjection());
        entity.setIsOptIn(dto.getIsOptIn());
        entity.setParentEarningId(dto.getParentEarningId());
        entity.setParentEarningName(dto.getParentEarningName());
        entity.setIsFormulaBasedCalculationSupported(dto.getIsFormulaBasedCalculationSupported());
        entity.setFormulaBasedOn(dto.getFormulaBasedOn());
        entity.setIsScheduledEarning(dto.getIsScheduledEarning());
        entity.setCanChangeDefaultPercentageVariables(dto.getCanChangeDefaultPercentageVariables());
        entity.setEarningFrequency(dto.getEarningFrequency());
        return entity;
    }
}
