package com.itsdev.payroll.mapper.statutorycomponents;


import com.itsdev.payroll.dto.statutorycomponents.EpfDTO;
import com.itsdev.payroll.entity.statutorycomponents.Epf;

public class EpfMapper {

    // Convert Entity → DTO
    public static EpfDTO toDto(Epf entity) {
        if (entity == null) {
            return null;
        }

        EpfDTO dto = new EpfDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setRegistrationNumber(entity.getRegistrationNumber());
        dto.setIsAdminChargesIncludedCtc(entity.getIsAdminChargesIncludedCtc());
        dto.setIsEdliIncludedCtc(entity.getIsEdliIncludedCtc());
        dto.setConsiderEarnedSalaryForEpf(entity.getConsiderEarnedSalaryForEpf());
        dto.setEpfAdminChargesEmployerContribution(entity.getEpfAdminChargesEmployerContribution());
        dto.setIsEmployerContributionIncludedCtc(entity.getIsEmployerContributionIncludedCtc());
        dto.setIsEmployeeRestrictedBasicEnabled(entity.getIsEmployeeRestrictedBasicEnabled());
        dto.setIsEmployerContributionIncludedSalaryStructure(entity.getIsEmployerContributionIncludedSalaryStructure());
        dto.setIsEligibleForAbryScheme(entity.getIsEligibleForAbryScheme());
        dto.setEdliEmployerContribution(entity.getEdliEmployerContribution());
        dto.setEpfEmployeeContribution(entity.getEpfEmployeeContribution());
        dto.setCanEnableEdliPfAdminChargesInSalaryStructure(entity.getCanEnableEdliPfAdminChargesInSalaryStructure());
        dto.setCanProRateRestrictedBasic(entity.getCanProRateRestrictedBasic());
        dto.setIsAssociatedWithEmployee(entity.getIsAssociatedWithEmployee());
        dto.setEpsSeniorCategoryAge(entity.getEpsSeniorCategoryAge());
        dto.setIsActive(entity.getIsActive());
        dto.setCanOverrideRestrictedBasic(entity.getCanOverrideRestrictedBasic());
        dto.setEpsEmployeeContribution(entity.getEpsEmployeeContribution());
        dto.setEpfAdminChargesEmployeeContribution(entity.getEpfAdminChargesEmployeeContribution());
        dto.setIsEdliIncludedSalaryStructure(entity.getIsEdliIncludedSalaryStructure());
        dto.setIsAdminChargesIncludedSalaryStructure(entity.getIsAdminChargesIncludedSalaryStructure());
        dto.setEpsEmployerContribution(entity.getEpsEmployerContribution());
        dto.setDeductionCycleFormatted(entity.getDeductionCycleFormatted());
        dto.setIsSubsidyApplicableForBothContributions(entity.getIsSubsidyApplicableForBothContributions());
        dto.setRegistrationDate(entity.getRegistrationDate());
        dto.setRegistrationDateFormatted(entity.getRegistrationDateFormatted());
        dto.setEpsEmployerContributionForSeniorcategory(entity.getEpsEmployerContributionForSeniorcategory());
        dto.setIsEmployerRestrictedBasicEnabled(entity.getIsEmployerRestrictedBasicEnabled());
        dto.setEpfEmployerContribution(entity.getEpfEmployerContribution());
        dto.setCanEnableEdliPfAdminChargesInCtc(entity.getCanEnableEdliPfAdminChargesInCtc());
        dto.setDeductionCycle(entity.getDeductionCycle());
        dto.setEpfEmployerContributionForSeniorcategory(entity.getEpfEmployerContributionForSeniorcategory());
        dto.setEdliEmployeeContribution(entity.getEdliEmployeeContribution());

        return dto;
    }

    // Convert DTO → Entity
    public static Epf toEntity(EpfDTO dto) {
        if (dto == null) {
            return null;
        }

        Epf entity = new Epf();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setRegistrationNumber(dto.getRegistrationNumber());
        entity.setIsAdminChargesIncludedCtc(dto.getIsAdminChargesIncludedCtc());
        entity.setIsEdliIncludedCtc(dto.getIsEdliIncludedCtc());
        entity.setConsiderEarnedSalaryForEpf(dto.getConsiderEarnedSalaryForEpf());
        entity.setEpfAdminChargesEmployerContribution(dto.getEpfAdminChargesEmployerContribution());
        entity.setIsEmployerContributionIncludedCtc(dto.getIsEmployerContributionIncludedCtc());
        entity.setIsEmployeeRestrictedBasicEnabled(dto.getIsEmployeeRestrictedBasicEnabled());
        entity.setIsEmployerContributionIncludedSalaryStructure(dto.getIsEmployerContributionIncludedSalaryStructure());
        entity.setIsEligibleForAbryScheme(dto.getIsEligibleForAbryScheme());
        entity.setEdliEmployerContribution(dto.getEdliEmployerContribution());
        entity.setEpfEmployeeContribution(dto.getEpfEmployeeContribution());
        entity.setCanEnableEdliPfAdminChargesInSalaryStructure(dto.getCanEnableEdliPfAdminChargesInSalaryStructure());
        entity.setCanProRateRestrictedBasic(dto.getCanProRateRestrictedBasic());
        entity.setIsAssociatedWithEmployee(dto.getIsAssociatedWithEmployee());
        entity.setEpsSeniorCategoryAge(dto.getEpsSeniorCategoryAge());
        entity.setIsActive(dto.getIsActive());
        entity.setCanOverrideRestrictedBasic(dto.getCanOverrideRestrictedBasic());
        entity.setEpsEmployeeContribution(dto.getEpsEmployeeContribution());
        entity.setEpfAdminChargesEmployeeContribution(dto.getEpfAdminChargesEmployeeContribution());
        entity.setIsEdliIncludedSalaryStructure(dto.getIsEdliIncludedSalaryStructure());
        entity.setIsAdminChargesIncludedSalaryStructure(dto.getIsAdminChargesIncludedSalaryStructure());
        entity.setEpsEmployerContribution(dto.getEpsEmployerContribution());
        entity.setDeductionCycleFormatted(dto.getDeductionCycleFormatted());
        entity.setIsSubsidyApplicableForBothContributions(dto.getIsSubsidyApplicableForBothContributions());
        entity.setRegistrationDate(dto.getRegistrationDate());
        entity.setRegistrationDateFormatted(dto.getRegistrationDateFormatted());
        entity.setEpsEmployerContributionForSeniorcategory(dto.getEpsEmployerContributionForSeniorcategory());
        entity.setIsEmployerRestrictedBasicEnabled(dto.getIsEmployerRestrictedBasicEnabled());
        entity.setEpfEmployerContribution(dto.getEpfEmployerContribution());
        entity.setCanEnableEdliPfAdminChargesInCtc(dto.getCanEnableEdliPfAdminChargesInCtc());
        entity.setDeductionCycle(dto.getDeductionCycle());
        entity.setEpfEmployerContributionForSeniorcategory(dto.getEpfEmployerContributionForSeniorcategory());
        entity.setEdliEmployeeContribution(dto.getEdliEmployeeContribution());

        return entity;
    }
}

