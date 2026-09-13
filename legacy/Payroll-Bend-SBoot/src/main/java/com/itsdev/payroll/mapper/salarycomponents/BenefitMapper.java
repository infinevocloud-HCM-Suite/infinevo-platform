package com.itsdev.payroll.mapper.salarycomponents;

import com.itsdev.payroll.dto.salarycomponents.BenefitDTO;
import com.itsdev.payroll.entity.salarycomponents.Benefit;

public class BenefitMapper {

    public static BenefitDTO toDTO(Benefit entity) {
        BenefitDTO dto = new BenefitDTO();
        dto.setBenefitId(entity.getBenefitId());
        dto.setBenefitName(entity.getBenefitName());
        dto.setBenefitPlan(entity.getBenefitPlan());
        dto.setBenefitPlanNameFormatted(entity.getBenefitPlanNameFormatted());
        dto.setBenefitCategory(entity.getBenefitCategory());
        dto.setPreTax(entity.isPreTax());
        dto.setEmployeeCount(entity.getEmployeeCount());
        dto.setStatus(entity.getStatus());
        dto.setOneTime(entity.isOneTime());
        dto.setUserConfigurable(entity.isUserConfigurable());
        dto.setProRata(entity.isProRata());
        dto.setSuperannuationBenefit(entity.isSuperannuationBenefit());
        dto.setIncludedInCtc(entity.isIncludedInCtc());
        dto.setIncludedInSalaryStructure(entity.isIncludedInSalaryStructure());
        dto.setTaxExemptionSubType(entity.getTaxExemptionSubType());
        dto.setTaxExemptionSubTypeFormatted(entity.getTaxExemptionSubTypeFormatted());
        dto.setTaxExemptSection(entity.getTaxExemptSection());
        dto.setCanAllowEmployerContribution(entity.isCanAllowEmployerContribution());
        dto.setCanAllowEmployeeContribution(entity.isCanAllowEmployeeContribution());
        return dto;
    }

    public static Benefit toEntity(BenefitDTO dto) {
        Benefit entity = new Benefit();
        entity.setBenefitName(dto.getBenefitName());
        entity.setBenefitPlan(dto.getBenefitPlan());
        entity.setBenefitPlanNameFormatted(dto.getBenefitPlanNameFormatted());
        entity.setBenefitCategory(dto.getBenefitCategory());
        entity.setPreTax(dto.isPreTax());
        entity.setEmployeeCount(dto.getEmployeeCount());
        entity.setStatus(dto.getStatus());
        entity.setOneTime(dto.isOneTime());
        entity.setUserConfigurable(dto.isUserConfigurable());
        entity.setProRata(dto.isProRata());
        entity.setSuperannuationBenefit(dto.isSuperannuationBenefit());
        entity.setIncludedInCtc(dto.isIncludedInCtc());
        entity.setIncludedInSalaryStructure(dto.isIncludedInSalaryStructure());
        entity.setTaxExemptionSubType(dto.getTaxExemptionSubType());
        entity.setTaxExemptionSubTypeFormatted(dto.getTaxExemptionSubTypeFormatted());
        entity.setTaxExemptSection(dto.getTaxExemptSection());
        entity.setCanAllowEmployerContribution(dto.isCanAllowEmployerContribution());
        entity.setCanAllowEmployeeContribution(dto.isCanAllowEmployeeContribution());
        return entity;
    }
}